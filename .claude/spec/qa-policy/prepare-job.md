# QA 준비 작업 (명세 로드, QA 준비)

근거: 2026-11 QA 흐름 재설계(D1~D14). 설계 문서 `03_DESIGN.md`의 셋업 작업(5-2), 확정(5-3), 자동 실행(5-4~5-6), guide(15)를 대신한다.
구현: `sprint/handler/SpecLoadJobHandler`, `sprint/handler/QaPrepareJobHandler`, `sprint/support/*Step`, `sprint/facade/SetupFacade`

## 스프린트 상태와 전이

실패는 상태로 두지 않는다. 마지막 작업의 `FAILED`·`CANCELLED`와 `setupSteps[].status = FAILED`로 본다.

| 지금 상태 | 계기 | 다음 상태 | 같은 트랜잭션에서 하는 일 |
|---|---|---|---|
| DRAFT | `POST /sprints/{id}/setup-jobs` | SPEC_LOAD | SPEC_LOAD 작업 등록 |
| SPEC_LOAD | 명세 수집 끝, 처리 안 한 반영 제안 0건 | CHECKLIST | SPEC_COLLECT `DONE`, QA_PREPARE 작업 등록 |
| SPEC_LOAD | 명세 수집 끝, 반영 제안 있음 | 그대로 | SPEC_COLLECT `WAITING`(메시지에 대기 건수) |
| SPEC_LOAD | 마지막 제안을 승인하거나 거절 | CHECKLIST | SPEC_COLLECT `DONE`, QA_PREPARE 작업 등록 |
| SPEC_LOAD(대기 중) | `setup-jobs` (재수집) | 그대로 | APPLIED가 아닌 제안, SPEC_COLLECT 단위 기록 삭제, SPEC_COLLECT `PENDING` |
| CHECKLIST → DESIGN_REVIEW → AUTO_REVIEW | 그 단계 끝 | 다음 상태 | 단계 `DONE`과 상태를 함께 저장 |
| AUTO_REVIEW | 1차 판정 끝 | MANUAL_REVIEW | |
| SPEC_LOAD~AUTO_REVIEW, 마지막 작업 실패·취소 | `setup-jobs` | 그대로 | 상태에 맞는 작업(SPEC_LOAD 또는 QA_PREPARE) 등록 |
| MANUAL_REVIEW | `close` | CLOSED | `checklist.md` |

- `setup-jobs`는 `{version}`을 받는다. 다르면 409 `GLOBAL_4091`. 실행 중인 작업이 있으면 409 `JOB_4091`, 그 밖에 시작할 수 없는 상태면 409 `SPRINT_4092`
- 스프린트당 실행 중인 작업은 종류마다 1개(`ux_job_active_per_sprint`). 시작 API는 종류와 상관없이 실행 중인 작업이 있으면 막는다
- 명세 로드 작업이 끝나기 전에 QA 준비 작업을 등록하므로, 인덱스를 종류별로 둔다(워커는 앞 작업이 끝난 뒤 다음 작업을 집는다)

## 작업과 단계

| 작업 | 단계(`JobType`, `SetupStepKey`) | 화면 표시 |
|---|---|---|
| SPEC_LOAD | CONNECT, SPEC_COLLECT | 연결, 명세 수집 |
| QA_PREPARE | CHECKLIST_GENERATE, DESIGN_REVIEW, AUTO_REVIEW | 체크리스트 생성, 디자인 검수, 1차 자동화 검수 |

- `setupSteps[]`의 원본은 `sprint_step`(스프린트·단계별 상태, 시도 횟수, 메시지)이다. `job_step`은 작업 1건의 진행 기록으로 남는다
- 시도 횟수는 단계가 시작될 때마다 1 늘어난다(자동 재시도, 다시 시작 모두)
- 단계는 끝날 때 결과를 바로 저장한다. 다시 실행하면 `DONE` 단계와 스프린트 상태가 이미 지난 단계는 `SKIPPED`("이전 실행에서 끝난 단계")로 건너뛴다
- LLM을 쓰는 단계는 화면(또는 화면 × 파트) 하나가 끝날 때마다 `prepare_unit`에 남기고, 다시 실행하면 끝난 단위를 건너뛴다. `detail`에 입력 길이, 호출 수, 보낸 파일 수, 실패 여부를 남긴다
- 작업이 끝내 실패하거나 취소되면 `JobTerminatedEvent`로 실행 중이던 단계를 `FAILED`로 바꾼다(`PrepareJobTerminatedListener`). 처리기 안의 실패는 `SetupStepRunner`가 바로 `FAILED`와 예외 메시지를 남긴다

## CONNECT (연결)

| 대상 | 확인 | 실패 메시지 |
|---|---|---|
| NOTION | 스프린트 행 읽기. `변경 요약`, `Figma QA 페이지`를 스프린트에 반영, `NOTION_SPRINT` 스냅샷 | Notion 응답 |
| FIGMA | 파일 키, QA 페이지, `{스프린트 이름}` Section | `Figma QA 페이지`가 비어 있음, URL 형식 아님, QA 페이지 없음, Section 없음 |
| SERVER, WEB, APP | gravit-server, gravit-web, gravit-aos의 `qa-baseline` HEAD SHA (`qa.adapter.github.qa-branch`) | `{저장소} 저장소에 qa-baseline 브랜치가 없습니다.` |

- 다섯 곳의 결과를 모두 `sprint_connection`에 쓴 뒤, 하나라도 실패하면 단계를 실패시킨다. 5xx·타임아웃이 섞여 있으면 재시도하고, 그 밖에는 재시도하지 않는다
- `connections[].ref`는 저장소면 `qa-baseline@{짧은 SHA}`. 1차 판정은 이 SHA의 코드를 읽는다

## SPEC_COLLECT (명세 수집)

- Notion 스프린트 `대상 기능` 페이지마다 속성과 본문을 읽고 `SpecPageParser`로 해석한다
  - 명세 맨 위 `항목 | 값` 표(코드에서는 기본 정보 표): 명세 상태, 파트, 라우트, 관련 API (`NotionSpecTableParser`)
  - 본문 표 행: `화면에 보이는 것`(C행), `사용자가 할 수 있는 것`(B행), `예외 상황`(E행) 제목 바로 다음 표. 첫 칸이 `C1`처럼 행 번호인 행만 쓴다 (`NotionSpecBodyParser`)
  - 수정 이력: 제목 `수정 이력` 바로 다음 표. 머리글로 `스프린트`, `수정 사항`, `반영일` 열을 찾는다 (`NotionHistoryTableParser`)
- 명세를 하나라도 읽지 못하면(맨 위 표 없음, 파트나 명세 상태 칸이 비었거나 모르는 값, 수정 이력 머리글에 열이 없음) 단계를 재시도 없이 실패시키고 `{화면 ID}: {이유}`를 모두 메시지에 담는다. 명세를 고친 뒤 다시 시작한다
- 대상 기능이 없거나 화면 ID가 겹치면 재시도 없이 실패
- 기준 명세(`sprint_feature`): 본문 마크다운(맨 위 표와 수정 이력 절 제외), C/B/E 행, 수정 이력 행, `changed`(이번 스프린트 행이 있음)
- 이번 스프린트 행 중 `반영일`이 빈 행이 있으면 반영 제안을 만든다(`reflection.md`). 처리 안 한 제안이 남으면 `WAITING`

## QA_PREPARE

| 단계 | 단위 | 하는 일 | 규칙 문서 |
|---|---|---|---|
| CHECKLIST_GENERATE | 화면 | OpenAPI 1회 수집(`OPENAPI` 스냅샷), 화면마다 체크 항목 생성 | `checklist.md` |
| DESIGN_REVIEW | 화면(WEB 또는 APP 파트) | QA 프레임 다시 읽기, 화면 프레임 레이어 읽기(`FIGMA_NODES` 스냅샷), 디자인 항목 생성과 판정 | `design-review.md` |
| AUTO_REVIEW | 화면 × 파트(AUTO 항목) | 연결 단계 SHA의 코드 선택, 판정, 셀 갱신 | `auto-review.md` |

- 단계가 끝날 때 메시지에 이번 실행 요약(항목 수, 호출 수, 실패·입력 잘림 수)을 남긴다

## LLM 입력 상한 (`qa.prepare`)

| 키 | 기본값 | 쓰는 곳 |
|---|---|---|
| `reflection-max-prompt-chars` | 30000 | 반영 제안 |
| `checklist-max-prompt-chars` | 30000 | 체크리스트 생성 |
| `design-review-max-prompt-chars` | 60000 | 디자인 검수(넘으면 플랫폼별로 나눠 호출, 그래도 넘으면 뒤를 자른다) |
| `auto-review-max-prompt-chars` | 80000 | 1차 판정(코드를 나눠 담는 기준) |
| `auto-review-max-chunks` | 4 | 1차 판정 단위당 최대 호출 수. 넘는 파일은 `droppedFiles`로 남긴다 |

- user 프롬프트 기준이다. 넘으면 뒤를 자르고 `(입력 길이 상한으로 이후 내용 생략)`을 붙인다
- 3rd Agile(60개) 기준 예상 호출 약 310회, 호출당 약 80초(job 1 INTERPRET 실측 71분 49초)로 6~7시간. 첫 실행 뒤 `prepare_unit.detail`로 다시 정한다

## 스냅샷

| source | 파일 | 단계 |
|---|---|---|
| `NOTION_SPRINT` | `{pageId}.json` | CONNECT |
| `NOTION_SPEC` | `{pageId}.json` (`page`, `table`, `specRows`, `history`, `markdown`, `blocks`, 실패 시 `error`) | SPEC_COLLECT |
| `OPENAPI` | `api-docs.json` | CHECKLIST_GENERATE |
| `FIGMA_NODES` | `{화면 ID}.json` (프레임 node ID → 레이어 글 개요) | DESIGN_REVIEW |

- 경로 `snapshots/{sprintId}/{jobId}/{source}/{파일}`. 1차 판정용 저장소 파일은 `repo-cache/{blob SHA}`에 둔다(같은 내용은 다시 받지 않는다)
