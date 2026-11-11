# 셋업 작업 (QA 준비하기)

근거: 설계 `03_DESIGN.md` 5-2, 10, 가이드 3-1, 3-3. 계획 PLAN-9(C4).
구현: `sprint/handler/SetupJobHandler`, `sprint/support/SetupCollector`, `sprint/support/SetupInterpreter`, `sprint/facade/SetupFacade`

## 시작 조건

- `POST /sprints/{id}/setup-jobs`: 스프린트가 `DRAFT`, `PREPARE_FAILED`, `REVIEW`일 때만. 스프린트를 `PREPARING`으로 바꾸고 `SETUP` 작업을 같은 트랜잭션에서 큐에 넣는다
- 실행 중(`QUEUED`/`RUNNING`) `SETUP` 작업이 있으면 409(`ux_job_active_per_sprint`)
- 처리기는 시작할 때 스프린트가 `PREPARING`이 아니면 재시도 없이 실패한다

## 단계

단계 이름은 `JobType.SETUP`을 그대로 쓴다. 단계 1~7은 DB의 스프린트 결과를 바꾸지 않고(스냅샷은 파일만 쓴다), 8단계에서 한 트랜잭션으로 반영한다. 재시도와 회수는 처음부터 다시 실행한다.

| # | 단계 | 처리 | 실패 |
|---|---|---|---|
| 1 | BASELINE | 3개 저장소 `release` HEAD SHA. 직전 **CLOSED** 스프린트(`closed_at` 최신) 기준점의 SHA를 `previous_commit_sha`로. `gravit-aos`는 `app/build.gradle.kts`에서 `versionCode`/`versionName`을 추출하고, 직전보다 크지 않으면 `version_code_warning` | 브랜치 없음(`ReleaseBranchNotFoundException`)은 재시도 없이 실패, 스프린트 `prepare_failure_reason = RELEASE_BRANCH_MISSING` |
| 2 | NOTION | 스프린트 행(`NOTION_SPRINT`), `대상 기능` 명세 페이지마다 속성 + 본문(`NOTION_SPEC`). 본문 첫 기본 정보 표 읽기, 라벨 변환(아래) | 대상 기능 0개, 화면 ID 중복, 읽을 수 있는 명세 0개는 재시도 없이 실패. 기본 정보 표를 읽지 못한 명세(표 없음, 명세 상태나 파트 없음, 모르는 라벨)는 그 명세만 빼고 `SPEC_READ_FAILED` 스프린트 경고를 남긴 뒤 계속 |
| 3 | FIGMA | `getQaFrames(fileKey, sprintKey)`. 기능 파트를 플랫폼으로 바꿔 화면을 찾고, 찾은 프레임만 PNG로 저장(`FIGMA_FRAME`) | Figma 파일 키 없음(Notion `Figma QA 페이지` 비어 있음)은 재시도 없이 실패. 프레임이 없는 기능은 실패가 아니라 `FIGMA_FRAME_MISSING` 경고 |
| 4 | DIFF | 저장소별 `compare(previous, current)`. 변경 파일 목록을 `GITHUB_DIFF`로. 같은 SHA면 호출하지 않고 빈 목록 | 직전 기준점이 없으면 `SKIPPED`("직전 기준점 없음 - 전체 범위") |
| 5 | OPENAPI | `/v3/api-docs` 1회(`OPENAPI`, 원문), 기능별 `관련 API`(`메서드 URI`)와 메서드, 경로가 정확히 같은 operation만 발췌 | 문서에 없는 API는 `OPENAPI_OPERATION_MISSING` 경고 |
| 6 | INTERPRET | 기능 1개당 `LlmClient.generate` 1회. 기능 사이마다 취소 요청 확인 | 출력 검증 3회 실패(`LlmOutputInvalidException`)는 그 기능만 "초안 생성 실패" 항목 1개 + `ITEM_GENERATION_FAILED`로 남기고 계속. `LlmRateLimitedException`, LLM 401/403은 재시도 없이 실패 |
| 7 | VALIDATE | 의미 검증과 같은 규칙에 "AUTO면 testRef가 카탈로그에 있음"을 더해 재확인 | 어긋나면 재시도 없이 실패(결정적이라 재시도해도 같음) |
| 8 | APPLY | 한 트랜잭션: 스프린트 `REVIEW`, `prepared_at`, Notion에서 다시 읽은 기간과 Figma 파일 키 반영 → 기준점 교체(행 유지, 저장소 행 재작성) → `feature` upsert → `sprint_feature` 교체 → `snapshot` 행 → 기존 `PROPOSED` 항목 삭제 후 새 항목, 셀 생성 | 스프린트가 `PREPARING`이 아니면 재시도 없이 실패 |

## 기능 명세 기본 정보 표

기능 명세 DB 속성은 `화면 ID`, `화면 이름`, `최종 검증일`만 읽는다(`스프린트` relation은 스프린트 쪽 `대상 기능`으로 따라간다). 나머지 값은 본문 첫 블록인 2열 표에서 읽는다(`NotionSpecTableParser`).

- 본문 첫 블록이 `table`이 아니면 그 명세는 읽지 못한 것으로 본다. 다른 위치의 표는 찾지 않는다
- 행은 첫 셀 텍스트(앞뒤 공백 제거)로 찾는다. 헤더 행(`항목 | 값`)과 모르는 행은 쓰지 않는다. 굵게, 인라인 코드 같은 서식은 무시한다
- 셀 값은 rich_text 조각의 plain_text를 이어 붙인 뒤 앞뒤 공백을 지운다. 빈 값과 `-`는 빈 값이다

| 항목 | 읽는 방법 | 빈 값일 때 | 쓰는 곳 |
|---|---|---|---|
| 명세 상태 | 값 그대로 | 그 명세 실패 | 라벨 변환(아래) |
| 파트 | `,`로 나누고 앞뒤 공백 제거 | 그 명세 실패 | 라벨 변환(아래) |
| 라우트 | 값 그대로(`/my, /my/summary`처럼 여러 개여도 문자열 하나) | null | `feature.route`, LLM 입력 |
| 관련 API | 줄바꿈으로 나눔 → 줄 앞 `•`, `-`, 공백 제거 → 공백을 1칸으로 → 빈 줄, 중복 제외 | 빈 목록 | `feature.related_apis`, OpenAPI 발췌, LLM 입력 |
| Figma | 셀 안 첫 링크의 URL | null | 저장하지 않음(스냅샷에만) |
| FE 스펙 ID | `,`로 나눔 | 빈 목록 | 저장하지 않음(스냅샷에만) |

- `관련 API` 한 줄은 `메서드 URI`(예: `GET /api/v1/problems/{lessonId}`)다. 형식을 검사하지 않고, OpenAPI에 같은 키가 없으면 `OPENAPI_OPERATION_MISSING`에 그대로 적힌다

## Notion 라벨 변환

| 표 항목 | Notion 라벨 | 값 |
|---|---|---|
| `파트` | 서버 / 웹 / 앱 | `SERVER` / `WEB` / `APP` |
| `명세 상태` | 역문서화 초안 / 확인 필요 / 확정 | `DRAFT` / `NEEDS_CHECK` / `CONFIRMED` |

- 앞뒤 공백만 무시하고 글자 그대로 비교한다. 모르는 라벨은 라벨 원문을 사유에 넣어 그 명세만 실패로 처리한다
- 기능 명세 `화면 이름`이 비어 있으면 `feature.name`은 화면 ID로 둔다

## 읽지 못한 명세

- 기본 정보 표 문제(표 없음, `명세 상태`나 `파트` 없음, 모르는 라벨)는 명세 1건의 문제라 그 명세만 이번 준비에서 뺀다. Figma, OpenAPI, LLM 단계와 `feature` upsert에 들어가지 않으므로 기존 `feature` 행은 그대로 남는다
- `sprint.warnings`에 `SPEC_READ_FAILED`(`{화면 ID}: {사유}`)를 남긴다. 다시 준비가 성공하면 이전 `SPEC_READ_FAILED`를 모두 지우고 새로 넣는다(다른 스프린트 경고는 유지)
- 읽지 못한 명세도 `NOTION_SPEC` 스냅샷은 남긴다(원인 확인용, 항목 `source_refs`에는 들어가지 않는다)
- 대상 기능 전부를 읽지 못하면 준비할 것이 없으므로 재시도 없이 실패한다
- 화면 ID 비어 있음과 중복은 그대로 작업 전체 실패다. 어느 행이 맞는지 정할 수 없는 정합성 문제라서다
- `feature`는 `notion_page_id`로 찾고, 없으면 `screen_id`로 찾아(Notion 페이지 재생성) 갱신한다. 둘 다 없으면 새로 만든다

## 파트 → Figma 플랫폼

| 파트 | 플랫폼 |
|---|---|
| `WEB` | `WEB` |
| `APP` | `AOS` |
| `SERVER` | 없음(Figma를 보지 않음) |

- 플랫폼마다 `findScreen(platform, screenId)`. 프레임이 없으면 그 플랫폼에 `FIGMA_FRAME_MISSING`을 남긴다. QA 페이지나 스프린트 Section이 없으면 메시지에 그 사유를 붙인다
- PNG 파일명 `{플랫폼}_{nodeId}.png`(`:`는 `-`로), `source_ref`는 node ID. 렌더에 실패한 노드는 로그만 남긴다
- `sprint_feature.figma_node_ids`는 그 기능의 이번 스프린트 프레임 node ID 전체다

## 스냅샷

| source | 파일 | `source_ref` | 기능 전용 |
|---|---|---|---|
| `NOTION_SPRINT` | `{pageId}.json` (페이지 원문) | 페이지 ID | 아니오 |
| `NOTION_SPEC` | `{pageId}.json` (`{page, table, markdown, blocks}`. 읽지 못한 명세는 `table: null`, 전체 본문 `markdown`, `error`) | 페이지 ID | 예 |
| `FIGMA_FRAME` | `{플랫폼}_{nodeId}.png` | node ID | 예 |
| `GITHUB_DIFF` | `{repo}.json` (`[{filename, status, additions, deletions, previousFilename}]`) | `repo@prev..cur` | 아니오 |
| `OPENAPI` | `api-docs.json` (원문) | OpenAPI URL | 아니오 |

- 경로는 `snapshots/{sprintId}/{jobId}/{source}/{파일}`이다. 재준비가 이전 작업의 파일을 덮어쓰면 유지되는 항목의 `source_refs` 해시가 어긋나므로 작업마다 디렉토리를 나눈다
- 파일은 단계 2~5에서 쓰고, `snapshot` 행은 APPLY에서 넣는다. 최종 실패한 작업의 파일은 행 없이 남는다
- 항목의 `source_refs`는 기능 공통 스냅샷(아니오) + 그 기능 전용 스냅샷(예)의 ID다

## LLM 입력

| 넣는 것 | 넣지 않는 것 |
|---|---|
| 기능 속성(화면 ID, 이름, 라우트, 파트, 명세 상태, 관련 API 목록), 명세 본문 마크다운(기본 정보 표를 뺀 블록), Figma 화면별 프레임 수와 상태 목록, 스프린트 `변경 요약`, OpenAPI 발췌(JSON), 저장소별 변경 파일 `{status} {path}` | Figma 이미지, diff patch 본문, 기본 정보 표의 Figma 링크와 FE 스펙 ID |

- 시스템 프롬프트(`prompts/checklist-draft-system.txt`): 규칙, 기준 5개 정의, 페르소나 목록(`PersonaKey` 키와 설명), 그 화면의 테스트 카탈로그 항목
- user 프롬프트(`prompts/checklist-draft-user.txt`)는 중요도 순으로 놓는다: 기능 속성 → 명세 본문 → Figma 상태 → 변경 요약 → OpenAPI 발췌 → 변경 파일
- 기본 정보 표를 본문에서 빼는 이유: 같은 값이 기능 속성과 본문에 두 번 들어가 토큰이 늘고, `-`와 `(없음)`처럼 표기가 갈린다. 파싱 결과를 기능 속성의 단일 출처로 둔다
- 본문 표 셀 안 줄바꿈은 `<br>`로 바꿔 마크다운 표 행이 깨지지 않게 한다
- 상한 `qa.setup.max-prompt-chars`(기본 30000자, user 프롬프트 기준). 넘으면 뒤에서 자르고 `(입력 길이 상한으로 이후 내용 생략)`을 붙인 뒤 `LLM_INPUT_TRUNCATED` 경고. 시작값이며 C6 파일럿에서 다시 정한다
- diff patch를 넣지 않는 이유: LLM 구독 한도를 면접 채점과 나눠 쓰고(C1-16), 토큰이 커진다. C6에서 다시 판단한다

## LLM 출력 타입

- 설계 11절의 `schemas/` 디렉토리 대신 Java record(`checklist/dto/internal/ChecklistDraftOutputDto` 외)로 정의하고 `LlmOutputValidator.schemaOf`가 JSON 스키마를 만든다(C3 방식)
- `BeanOutputConverter`는 모든 속성을 required, 비널로 만든다. null을 허용할 컴포넌트에는 `org.springframework.lang.Nullable`을 붙이고, `LlmOutputValidator`가 그 속성의 `type`을 `[T, "null"]`로 바꾼다(LLM에 보내는 형식 안내의 스키마도 같다)
- 항목 필드: `evidence`(근거, 맨 앞), `title`, `expected`, `parts`, `criteria`(5개, 각각 `reason` → `met`), `personaKey`(nullable), `testRef`(nullable)
- `evidence`는 저장하지 않는다(테이블과 API 계약에 자리 없음). 근거를 먼저 쓰게 해 판정 품질을 높이는 용도다

## 의미 검증 (출력마다, 실패 시 오류를 붙여 재요청 최대 2회)

- 항목 1개 이상
- `evidence`, `title`, `expected` 공백 아님
- `parts` 1개 이상, 중복 없음, 기능 파트의 부분집합
- `personaKey`는 null 또는 페르소나 목록에 있음
- `testRef`는 null 또는 테스트 카탈로그에 있음

## 분류 (코드가 결정)

| 기준 5개 | testRef | 결과 |
|---|---|---|
| 모두 `met` | 카탈로그에 있음 | `AUTO` |
| 모두 `met` | 없음 | `MANUAL` + `automation_gap = true` |
| 하나라도 미충족 | 무관 | `MANUAL` |

- 테스트 카탈로그(`test-catalog.json`)는 C7 전까지 비어 있으므로 C4에서는 `AUTO`가 나오지 않는 것이 정상이다

## 누락 처리 (설계 5-2)

- 항목은 만들되 `expected` 앞에 코드가 표기를 붙인다(LLM에 맡기지 않음)
  - 명세가 `NEEDS_CHECK`: `근거 없음(명세 확인 필요) - `
  - 항목 파트(WEB/APP)의 플랫폼 프레임이 없음: `근거 없음(Figma {WEB|AOS} 프레임 없음) - `
- 경고: `SPEC_NEEDS_CHECK`, `FIGMA_FRAME_MISSING`

## 초안 생성 실패 항목

| 필드 | 값 |
|---|---|
| title | `초안 생성 실패` |
| expected | 실패 안내 + 마지막 검증 오류(500자까지) |
| parts | 기능 파트 전체 |
| classification | `MANUAL`, criteria null, gap false |

## 셀 생성

- 항목마다 `SERVER`, `WEB`, `APP` 3개를 모두 만든다. 항목 `parts`에 있는 파트는 `UNCHECKED`, 없는 파트는 `NOT_APPLICABLE`, source `MANUAL`

## 기능 경고 (`sprint_feature.warnings`)

`[{code, message}]`. 코드는 `ReportWarning.code` 값을 쓰고, C4에서 2개를 더했다.

| 코드 | 남는 때 |
|---|---|
| `SPEC_NEEDS_CHECK` | 명세 상태가 확인 필요 |
| `FIGMA_FRAME_MISSING` | WEB/APP 파트인데 그 플랫폼 프레임 없음(플랫폼별 1개) |
| `OPENAPI_OPERATION_MISSING` | `관련 API`의 `메서드 URI`가 dev OpenAPI에 없음(형식이 틀린 줄 포함) (C4 추가, #15에서 operationId 기준을 바꿈) |
| `LLM_INPUT_TRUNCATED` | 프롬프트가 상한을 넘어 잘림 (C4 추가) |
| `ITEM_GENERATION_FAILED` | LLM 출력 검증 3회 실패 |

- 추가한 2개는 `ChecklistFeature.warnings`(string 배열)에는 그대로 나간다. `ReportWarning.code` enum은 리포트 단계에서 넓혀야 한다
- `versionCode` 미증가는 기능 경고가 아니라 `baseline_repo.version_code_warning`에 남는다

## 스프린트 경고 (`sprint.warnings`)

| 코드 | 남는 때 |
|---|---|
| `SPEC_READ_FAILED` | 준비 중 기본 정보 표를 읽지 못해 뺀 명세(명세마다 1개, 메시지 `{화면 ID}: {사유}`). 준비가 성공할 때마다 교체 (#15 추가) |
| `NOTION_WRITE_FAILED` | 종료 시 Notion `QA 기준점` 기록 실패(`checklist.md`) |

- `GET /sprints/{id}` 응답의 `warnings`로 내려간다

## 스프린트 상태 동기화

- 작업이 최종 `FAILED`/`CANCELLED`가 되면 job이 `JobTerminatedEvent`를 발행하고, `SetupJobTerminatedListener`가 스프린트를 `PREPARE_FAILED`로 바꾼다(`PREPARING`일 때만)
- 발행 지점: 재시도 소진·재시도 불가 실패, 실행 중 취소, heartbeat 회수로 시도 소진 또는 취소, API의 대기 중 취소
- 작업 상태 기록과 같은 트랜잭션에서 동기로 처리한다. "작업은 끝났는데 스프린트는 PREPARING"인 막다른 상태를 만들지 않기 위해서다
- 처리기 콜백이 아니라 이벤트인 이유: 대기 중 취소는 처리기 목록이 없는 `JobCommandService`에서 일어나고, 회수는 유형 필터 없이 어느 워커에서나 일어난다
- `REVIEW`에서 다시 준비하다 실패해도 `PREPARE_FAILED`가 된다. 기존 항목은 남지만 `REVIEW`로 돌아가려면 준비를 다시 성공시켜야 한다
