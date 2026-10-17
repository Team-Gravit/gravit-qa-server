# 체크리스트, 확정, 종료

근거: 설계 `03_DESIGN.md` 5-1, 5-3, 5-5, 5-7, 6, 15-3, OpenAPI. 계획 PLAN-9(C4).
구현: `checklist/facade/ChecklistFacade`, `checklist/service/ChecklistCommandService`, `checklist/support/CheckCellEditPolicy`, `sprint/facade/SprintFacade`, `sprint/support/SprintGuideCalculator`

## 스프린트 등록

- Notion 스프린트 페이지 URL로 행을 읽어 `DRAFT`로 만든다. `sprint_key` = Notion `이름`, 기간, `figma_file_key` = `Figma QA 페이지` URL의 파일 키, `created_by` = `adminId`
- 기간, Figma URL이 비어 있어도 등록된다(null). Figma URL이 있는데 파일 URL 형식이 아니면 400
- 같은 Notion 페이지 또는 같은 `sprint_key`는 409

| Notion 응답 | 결과 |
|---|---|
| 404(Integration 미연결 포함) | 404 `SPRINT_4042` |
| 스프린트 DB 행이 아님, URL에 페이지 ID 없음, `이름` 비어 있음 | 400 `SPRINT_4001` |
| 5xx 재시도 소진, 401/403 | 502 `SPRINT_5021` (OpenAPI에 없는 코드) |

## 상태별로 할 수 있는 일

| 스프린트 상태 | 항목 추가·수정·거절 | 셀 체크 | 확정 | 종료 | 준비하기 |
|---|---|---|---|---|---|
| DRAFT, PREPARE_FAILED | 불가 | 불가 | 불가 | 불가 | 가능 |
| PREPARING | 불가 | 불가 | 불가 | 불가 | 불가 |
| REVIEW | **가능** | 불가 | **가능** | 불가 | 가능(재준비) |
| IN_PROGRESS | 불가 | **가능** | 불가 | **가능** | 불가(C1-3) |
| CLOSED | 불가 | 불가 | 불가 | 불가 | 불가 |

상태가 맞지 않으면 409 `SPRINT_4092`.

## 항목

- 사람이 추가한 항목: `origin = HUMAN`, `review_state = CONFIRMED`, criteria null. 스프린트 대상 기능(`sprint_feature`)이 아니면 404
- 수정은 전달한 필드만 바꾼다. `testRef`, `personaKey`는 필드가 없으면 유지, null이면 해제(`JsonNullable`)
- `reviewState`는 `CONFIRMED`/`REJECTED`만. `PROPOSED`로 되돌리기는 400
- `classification = AUTO`이면 `testRef`가 테스트 카탈로그에 있어야 한다(없으면 422). `AUTO`로 바꾸면 `automation_gap = false`
- `testRef`는 null이 아니면 카탈로그에 있어야 하고, `personaKey`는 null이 아니면 페르소나 목록에 있어야 한다(422). 생성도 같다(OpenAPI 생성에는 422가 없다)
- `parts` 변경: 빠진 파트의 셀은 `NOT_APPLICABLE`, 다시 들어온 파트는 `UNCHECKED`. 바뀐 셀마다 이력을 남긴다
- 낙관적 잠금: 요청 `version`이 다르면 409 `GLOBAL_4091`. 동시 커밋 충돌(`OptimisticLockingFailureException`)도 같은 코드

## 재준비 시 보존

- `PROPOSED` 항목만 지우고 새 초안으로 바꾼다. `CONFIRMED`, `REJECTED`, `HUMAN` 항목은 남는다
- 새 항목의 `sort_order`는 그 기능의 기존 최댓값 다음부터
- 대상 기능에서 빠진 기능이라도 남은 항목이 있으면 체크리스트 조회에 기능 그룹으로 계속 보인다(Figma 노드, 경고는 빈 값). 종료 조건에 잡히는 셀이 화면에서 사라지지 않게 하려는 것이다

## 확정

- `REVIEW`에서 모든 항목이 `CONFIRMED`/`REJECTED`여야 한다(남은 `PROPOSED`가 있으면 409 `CHECKLIST_4091`). 항목이 0개여도 확정된다
- `IN_PROGRESS`, `confirmed_at`. 스프린트 `version` 확인
- `CONFIRMED` `AUTO` 항목이 있고 `startAutoRun`이 false가 아니면(기본 true) 같은 트랜잭션에서 `AUTO_RUN` 작업과 `auto_run`(QUEUED, 현재 기준점)을 만든다. 소비 워커는 C7
- 수동 페르소나 세트 재적재(C1-14)는 C7 범위라 아직 하지 않는다

## 셀 체크

- `IN_PROGRESS`에서만(409). 요청 상태는 `UNCHECKED`, `PASS`, `FAIL`만(`NOT_APPLICABLE`은 400)
- 수동 변경 불가(422 `CHECKLIST_4223`)
  - `AUTO` 항목의 `WEB`, `SERVER` 셀(자동 실행으로만 갱신, C1-7)
  - `NOT_APPLICABLE` 셀
  - `REJECTED` 항목의 셀
- 같은 상태로 바꾸면 이력 없이 그대로 돌려준다. 바뀌면 `check_cell_history`(이전, 새 상태, 변경자, 시각)
- 응답의 `editable` = 스프린트 `IN_PROGRESS` && 위 불가 조건에 해당하지 않음. `REVIEW`에서는 항상 false

## 진행률, 종료 조건

- 진행률(`PartProgress`)은 `REJECTED` 항목을 빼고 파트별로 센다. `total`은 `NOT_APPLICABLE` 제외. 항목이 없어도 파트 3개를 내려준다
- 실행 중 자동 실행 = 그 스프린트의 `QUEUED`/`RUNNING` `AUTO_RUN` 작업. `auto_run.status`는 C7 전까지 바뀌지 않으므로 작업 기준으로 본다(작업을 취소하면 종료가 막히지 않음)
- 종료: `IN_PROGRESS`, `UNCHECKED` 셀 0(거절 항목 제외), 실행 중 자동 실행 없음, 스프린트 `version` 일치. FAIL은 허용
- 종료가 커밋된 뒤 Notion `QA 기준점`에 기준점 요약을 쓴다. 저장소마다 한 줄 `{repo} {branch} {commitSha}`, `gravit-aos`에는 ` (versionCode n, versionName v)`
- Notion 기록이 실패하면 종료는 그대로 두고 `sprint.warnings`에 `NOTION_WRITE_FAILED`를 남긴다

## 경고 저장 위치

| 범위 | 위치 | 코드 |
|---|---|---|
| 스프린트 | `sprint.warnings` `[{code, message}]` | `NOTION_WRITE_FAILED` |
| 기능(스프린트별) | `sprint_feature.warnings` | `setup-job.md`의 기능 경고 표 |
| 저장소 | `baseline_repo.version_code_warning` | `VERSION_CODE_NOT_INCREASED`(리포트에서 변환) |

리포트(후속)가 세 곳을 합쳐 `ReportWarning`을 만든다. `ChecklistFeature.warnings`는 코드만 중복 없이 내려준다(설계 15-1).

## 화면 안내 (`guide`, 설계 15-3)

`stageCount = 5`, `stageIndex`는 PREPARE=1, REVIEW=2, CHECK=3, CLOSE=4, DONE=5.

| 스프린트 상태 | 조건 | stage | nextAction | blockers |
|---|---|---|---|---|
| DRAFT | | PREPARE | `START_SETUP` | - |
| PREPARING | | PREPARE | `WAIT_SETUP` | `SETUP_IN_PROGRESS` |
| PREPARE_FAILED | 사유 = 브랜치 없음 | PREPARE | `RETRY_SETUP` | `RELEASE_BRANCH_MISSING` |
| PREPARE_FAILED | 그 외 | PREPARE | `RETRY_SETUP` | - |
| REVIEW | 남은 PROPOSED > 0 | REVIEW | `REVIEW_ITEMS` | `PROPOSED_ITEMS_REMAINING`(count) |
| REVIEW | 남은 PROPOSED = 0 | REVIEW | `CONFIRM_CHECKLIST` | - |
| IN_PROGRESS | 실행 중 자동 실행 있음 | CHECK | `WAIT_AUTO_RUN` | `AUTO_RUN_IN_PROGRESS` + 남은 파트별 `UNCHECKED_CELLS_REMAINING` |
| IN_PROGRESS | UNCHECKED 있음 | CHECK | `CHECK_CELLS` | 파트별 `UNCHECKED_CELLS_REMAINING`(count, part, 0인 파트는 뺌) |
| IN_PROGRESS | 둘 다 없음 | CLOSE | `CLOSE_SPRINT` | - |
| CLOSED | | DONE | `NONE` | - |

- 준비 실패 사유는 `sprint.prepare_failure_reason`에 남는다. 준비를 다시 시작하거나 성공하면 지운다
