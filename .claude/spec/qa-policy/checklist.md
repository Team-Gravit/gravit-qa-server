# 체크리스트, 셀, 종료

구현: `checklist/support/ChecklistGenerator`, `ChecklistDraftValidator`, `checklist/facade/ChecklistFacade`, `checklist/service/ChecklistCommandService`, `sprint/facade/SprintFacade`

## 생성 (CHECKLIST_GENERATE, D6~D8)

- 화면 1개당 LLM 1회. 입력: 기능 속성(화면 ID, 이름, 라우트, 파트, 명세 상태, 관련 API, 변경 여부), 다뤄야 할 행 번호, 기준 명세 본문, 스프린트 `변경 요약`, OpenAPI 발췌
- 프롬프트: `prompts/checklist-draft-{system,user}.txt`
  - D7: 같은 화면 영역이나 같은 사용자 행동은 한 항목으로 묶고 세부 확인 내용은 설명(`expected`)에 넣는다. 결과가 다른 분기는 따로 만든다
  - D8: AUTO는 코드를 읽어 판정할 수 있는 것(표시 문구, 순서, 조건 분기, API 요청과 응답), MANUAL은 실제로 동작시켜야 하는 것(애니메이션, 타이밍, 기기·브라우저 의존, 외부 로그인 창 자체). 판단이 어려우면 MANUAL
- 출력 항목: `evidence`, `specRowIds`, `title`(행위), `expected`(설명), `parts`, `classificationReason`, `classification`. `evidence`, `classificationReason`은 저장하지 않는다
- 코드 검증(묶음 검증, 실패하면 오류를 붙여 최대 2회 다시 요청):
  - 항목 1개 이상, 빈 필드 없음, 같은 행위(`title`) 항목 없음
  - `parts`는 기능 파트 안, 중복 없음
  - `specRowIds`는 명세에 있는 행 번호이고 항목마다 1개 이상(명세에 행이 없으면 검사하지 않음)
  - C/B/E 행을 모두 한 번 이상 다룸
  - 다루는 행이 모두 `[웹]` 행이면 APP, 모두 `[앱]` 행이면 WEB을 넣지 않는다(행에 둘 다 없으면 모든 플랫폼)
- 명세 상태가 '확인 필요'면 설명 앞에 `근거 없음(명세 확인 필요) - `를 붙인다
- 검증을 3번 모두 통과하지 못하면 "초안 생성 실패" 항목 1개(MANUAL, 기능 파트 전체, 설명에 오류 500자까지)
- 생성한 항목은 바로 확정된다(분류 검토 단계 없음). 셀은 `parts`에 있으면 UNCHECKED, 없으면 NOT_APPLICABLE

## 1차, 2차 체크리스트

| 조회 | 항목 |
|---|---|
| `GET /sprints/{id}/checklist?round=1` | AUTO (1차) |
| `GET /sprints/{id}/checklist?round=2` | MANUAL (2차) |

- `round`가 없거나 1, 2가 아니면 400 `GLOBAL_4001`
- 화면별로 묶는다(`featureId`, `screenId`, `screenName`, `changed`, `completionRate`, `items`). 항목이 없는 화면은 빠진다
- `completionRate`: 그 화면 셀 중 NOT_APPLICABLE을 뺀 수에서 UNCHECKED가 아닌 비율(0~100, 버림). 셀이 없으면 100

## 상태별로 할 수 있는 일

| 스프린트 상태 | 항목 추가, 수정, 삭제 | 셀, 디자인 항목 변경 | 종료 |
|---|---|---|---|
| DRAFT, SPEC_LOAD, CHECKLIST | 불가 | 불가 | 불가 |
| DESIGN_REVIEW, AUTO_REVIEW | 실행 중인 작업이 없을 때(실패로 멈춘 때) | 불가 | 불가 |
| MANUAL_REVIEW | 가능 | 가능 | 가능 |
| CLOSED | 불가 | 불가 | 불가 |

상태가 맞지 않거나 실행 중인 작업이 있으면 409 `SPRINT_4092`. 디자인 항목의 행위, 설명 수정과 삭제는 MANUAL_REVIEW에서만 된다(`design-review.md`).

## 항목 편집

- 사람이 추가한 항목: `origin = HUMAN`, `specRowIds` 없음, `classification = MANUAL` 고정. 스프린트 대상 기능이 아니면 404 `CHECKLIST_4042`
  - 1차(AUTO)는 LLM 판정 대상인데 추가한 항목에는 1차 판정이 다시 돌지 않으므로 2차로 둔다
  - 요청의 `classification`은 받되 무시한다(옛 admin 호환). 값이 AUTO여도 MANUAL로 저장한다
- 수정은 전달한 필드만 바꾼다. 행위, 설명, 파트, 분류 중 하나라도 바뀌면 그 항목의 셀 중 판정이 있는 셀(NOT_APPLICABLE 제외)을 UNCHECKED, `source = MANUAL`로 되돌리고 이력 `reason = "항목 수정으로 판정 초기화"`
- `parts` 변경: 빠진 파트는 NOT_APPLICABLE, 다시 들어온 파트는 UNCHECKED
- 분류는 AUTO에서 MANUAL로만 바꿀 수 있다(MANUAL → AUTO는 400 `CHECKLIST_4002`, 같은 값은 변경 없음). 옮기면 2차 체크리스트에 나타나고 1차 판정을 다시 돌리지는 않는다(사람이 확인)
- 삭제: `DELETE /checklist-items/{id}?version=` → 204. 셀과 셀 이력도 지워진다
- 낙관적 잠금: `version`이 다르면 409 `GLOBAL_4091`

## 셀

- 상태 UNCHECKED, PASS, FAIL, NOT_APPLICABLE. 출처 AUTO(1차 판정), MANUAL(사람)
- 변경 요청은 UNCHECKED, PASS, FAIL만(NOT_APPLICABLE은 400 `CHECKLIST_4001`). NOT_APPLICABLE 셀은 422 `CHECKLIST_4223`
- 1차 판정 결과도 사람이 바꿀 수 있다. 사람이 바꾸면 `source = MANUAL`, `reason = null`, `evidence = []`
- `evidence`: 1차 판정 근거 코드 위치 `[{path, startLine, endLine}]`(`path`는 `{저장소}/{경로}`). 1차 판정만 채우고, 사람 변경과 판정 초기화는 빈 배열
- 바뀌면 `check_cell_history`(이전, 새 상태, 출처, 사유, 근거, 변경자, 시각). 1차 판정은 상태가 같아도 사유와 근거를 함께 이력으로 남긴다
- `editable` = MANUAL_REVIEW이고 NOT_APPLICABLE이 아님

## 진행도 (`progress[]`)

| 파트 | 대상 |
|---|---|
| DESIGN | 디자인 검수 항목 |
| WEB, APP, SERVER | 1차, 2차 체크 셀 합계 |

- `total` = NOT_APPLICABLE을 뺀 수, `checked` = PASS와 FAIL 수, `pass`, `fail`. 디자인 항목의 해당 없음(사람이 고름)과 체크 셀의 해당 없음(파트 제외)을 같은 규칙으로 뺀다

## 종료

- MANUAL_REVIEW에서만(409 `SPRINT_4092`), 실행 중인 작업이 없을 때(409 `JOB_4091`), `version` 일치
- 체크 셀과 디자인 항목의 UNCHECKED가 0이어야 한다(409 `SPRINT_4093`). FAIL, NOT_APPLICABLE은 허용
- Notion에 아무것도 쓰지 않는다
