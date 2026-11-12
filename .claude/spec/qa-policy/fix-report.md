# 수정사항 파일 (파트별 TXT)

구현: `fixReport/facade/FixReportFacade`, `fixReport/support/FixReportAssembler`(묶음·정렬), `FailReasonSelector`(사유·근거), `FixReportWriter`(TXT), `FixReportFileName`. 조회는 `ChecklistQueryService.getFailedCells`, `DesignReviewQueryService.getFailedItems`

QA 검수(디자인 검수, 1차·2차 체크리스트)가 끝나면 Fail 항목을 디자인·웹·앱·서버 담당(사람 또는 코딩 에이전트)에게 넘긴다. 파트별로 수정에 필요한 최소 정보만 담은 TXT 파일을 서버가 만든다.

- LLM을 쓰지 않는다. DB 값을 정해진 틀에 채운다
- 읽기 전용이다. 파일을 만들 때 DB에 쓰지 않는다
- 형식은 TXT(UTF-8, 줄 끝 LF). 받는 쪽이 원문 그대로 읽으므로 마크다운 문법(표, `#`, 굵게)은 쓰지 않는다
- 파트별 파일만 내려준다. ZIP은 만들지 않는다

## API

| 요청 | 응답 |
|---|---|
| `GET /sprints/{id}/fix-reports` | `200 {sprintId, status, ready, unchecked: {design, first, second}, parts: [{part, failCount, fileName}]}`. 없는 스프린트 404 `SPRINT_4041` |
| `GET /sprints/{id}/fix-reports/{part}` | `200 text/plain;charset=UTF-8` 첨부 파일. `ready = false`면 409 `FIX_REPORT_4091`, 잘못된 `part`면 400 `GLOBAL_4001`, 없는 스프린트 404 `SPRINT_4041` |

- `part`는 `DESIGN`, `WEB`, `APP`, `SERVER`(대소문자 구분). 경로 값 변환이 먼저라 없는 스프린트에 잘못된 `part`를 보내면 400이다
- `parts`는 항상 4개이고 순서는 DESIGN, WEB, APP, SERVER
- `failCount`: DESIGN은 디자인 항목 중 FAIL 수, WEB·APP·SERVER는 그 파트 체크 셀 중 FAIL 수(1차·2차 합계). `ready`와 관계없이 지금 값이다. 진행도(`progress[].fail`)와 같은 집계를 쓴다
- `unchecked.design`: 디자인 항목 UNCHECKED 수. `unchecked.first`: 1차(분류 AUTO 항목) 셀의 UNCHECKED 수. `unchecked.second`: 2차(분류 MANUAL 항목) 셀의 UNCHECKED 수. 셀의 출처(`source`)와는 별개다
- `Content-Disposition: attachment; filename="{fileName}"; filename*=UTF-8''{인코딩한 fileName}`. 파일 이름은 항상 ASCII라 인코딩해도 원래 이름과 같다

## 내려받을 수 있는 조건 (`ready`)

스프린트 종료 조건(`checklist.md` 종료)과 같다. 종료 조건을 바꾸면 이 조건도 함께 바꾼다.

- `MANUAL_REVIEW`이고, 실행 중인 작업이 없고, `unchecked`의 셋이 모두 0
- 또는 `CLOSED`

`ready`를 확인한 뒤 파일을 만드는 사이에 사람이 셀을 바꾸면 바뀐 값이 파일에 들어간다. 내려받는 시점의 사본이다.

## 파일 이름

- `{slug}_{PART}_fixes.txt` (예: `test-agile_SERVER_fixes.txt`, `3rd-agile_DESIGN_fixes.txt`)
- `slug`: `sprintKey`를 소문자로 바꾸고, `[a-z0-9]`이 아닌 글자가 이어지면 `-` 하나로 바꾼 뒤 앞뒤 `-`를 지운다. 결과가 비면 `sprint-{id}`

## 틀

웹·앱·서버(WEB, APP, SERVER):

```
{sprintKey} / {PART} 수정사항 {n}건
기준 코드: {저장소} {branch}@{SHA 앞 7자리}
기대 결과 우선순위: Notion 명세 > Figma 시안 > 코드

[{screenId}] {screenName}
명세: https://www.notion.so/{notionPageId에서 '-'를 뺀 값}

1. {항목 행위(title)}
   기대: {항목 설명(expected)}
   문제: {사유}
   명세 행: {specRowIds를 ", "로 이음}
   코드: {path}:{startLine}-{endLine}
```

디자인(DESIGN):

```
{sprintKey} / DESIGN 수정사항 {n}건
기준 시안: Figma {figmaFileKey}
기대 결과 우선순위: Notion 명세 > Figma 시안 > 코드

[{screenId}] {screenName} · {웹|앱}
명세: https://www.notion.so/{notionPageId에서 '-'를 뺀 값}

1. {title}
   기대: {expected}
   문제: {사유}
   명세 행: {specRowIds}
   시안: https://www.figma.com/design/{figmaFileKey}?node-id={nodeId의 ':'를 '-'로}
```

- 화면 묶음 사이, 항목 사이에 빈 줄 하나를 둔다. 파일은 LF 하나로 끝난다
- `{n}`은 그 파일의 항목 수. 번호는 파일 전체에서 1부터 이어 붙인다(담당자가 "3번"으로 부를 수 있게)

| 규칙 | 내용 |
|---|---|
| 대상 | WEB·APP·SERVER: 그 파트 체크 셀 중 `state = FAIL`(1차·2차 모두). DESIGN: 디자인 항목 중 `state = FAIL` |
| 여러 파트에서 Fail인 항목 | 파트마다 해당 파일에 한 번씩 들어간다 |
| 순서 | 화면 `screenId` 오름차순(같으면 기능 ID) → (디자인) 웹, 앱 → 1차 항목, 2차 항목 → 항목 `sortOrder` → 항목 ID |
| 저장소 | WEB `gravit-web`, APP `gravit-aos`, SERVER `gravit-server` |
| 기준 코드 줄 (D8) | 그 스프린트 연결 기록의 `branch`와 SHA로 `ConnectionResponse.ref`와 같은 규칙(`{branch}@{SHA 앞 7자리}`)을 쓴다. 연결 기록이 없거나 SHA가 없으면 줄을 뺀다 |
| 기준 시안 줄 | 스프린트 `figmaFileKey`. 없으면 이 줄과 모든 `시안:` 줄을 뺀다 |
| 웹·앱 표기 | 디자인 플랫폼 WEB → `웹`, APP → `앱` |
| 명세 행 | 비었으면(사람이 추가한 체크 항목) 줄을 뺀다 |
| 시안 | 디자인 항목의 `figmaNodeIds`마다 한 줄. 없으면 줄을 뺀다 |

### 값이 없을 때, 여러 줄일 때 (D5)

- 값이 없는 줄은 통째로 뺀다
- 값 안의 CRLF, CR은 LF로 바꾼다. 각 줄의 끝 공백을 지우고, 값 안의 빈 줄은 지운다(빈 줄은 묶음과 항목 구분에만 쓴다)
- 여러 줄 값은 둘째 줄부터 공백 3칸으로 시작한다. 번호 줄(`1. {title}`)도 같다. 앞쪽 들여쓰기는 그대로 둔다
- 머리 줄(`sprintKey`, 화면 이름)은 줄바꿈을 공백 하나로 바꿔 한 줄로 쓴다

```
1. 이전·다음 공지로 이동한다
   기대: 상세 응답에 이전·다음 공지 정보가 있다
   이전 글이 없으면 null이다
   문제: 응답 DTO에 이전·다음 공지 필드가 없다
```

### Fail 0건 (D4)

200으로 제목 줄과 기준 줄만 남기고(우선순위 줄은 뺀다), 빈 줄 하나 뒤에 `수정할 항목이 없습니다.` 한 줄을 쓴다. 기준 줄이 없으면 제목 줄만 남는다.

```
Test Agile / SERVER 수정사항 0건
기준 코드: gravit-server qa-baseline@0eced9c

수정할 항목이 없습니다.
```

## 사유와 근거 고르기 (D2, D3)

사유와 코드 근거는 따로 고른다. 고르는 범위의 "기록"은 그 셀(디자인 항목)의 이력 중 마지막 판정 초기화 기록보다 뒤의 기록이다.

- 판정 초기화 기록: `reason = "항목 수정으로 판정 초기화"`이고 `toState`가 FAIL이 아닌 이력. 체크 항목의 행위·설명·파트·분류 수정(`resetJudgment`, `applyParts`), 디자인 항목의 행위·설명 수정(`resetJudgment`)이 남긴다
- 항목 내용이 바뀌면 상태와 관계없이 초기화 기록이 남는다. 이미 사람이 둔 UNCHECKED여도 UNCHECKED → UNCHECKED로 남으므로 수정 전 기록은 항상 범위 밖이다(`checklist.md`, `design-review.md` 항목 편집)
- 초기화 전 기록은 고치기 전 내용에 대한 판정이라 고르지 않는다

| 값 | 고르는 순서 |
|---|---|
| 사유 | ① 셀(항목)의 지금 `reason` ② 범위 안에서 가장 최근의 `toState = FAIL`이면서 `reason`이 있는 기록의 사유 ③ `(사유 없음)` |
| 코드 근거 (WEB·APP·SERVER) | ① 셀의 지금 `evidence` ② 범위 안에서 가장 최근의 `toState = FAIL`이면서 `evidence`가 있는 기록의 근거 ③ 없으면 `코드:` 줄을 뺀다 |

디자인 이력에는 근거 칸이 없으므로 디자인은 사유만 고른다.

| 경우 | 사유 | 근거 |
|---|---|---|
| 1차 FAIL(사유 R1, 근거 E1) 그대로 | R1 | E1 |
| 1차 FAIL → 사람 PASS → 사람 FAIL(사유 없음) | R1 | E1 |
| 1차 FAIL → 사람 PASS → 사람 FAIL(사유 R2) | R2 | E1 |
| 1차 FAIL → 사람이 FAIL 그대로 사유만 R2로 고침 | R2 | E1(셀에 그대로 남음) |
| 1차 PASS → 사람 FAIL(사유 R2) | R2 | 없음 |
| 1차 FAIL → 항목 수정(초기화) → 사람 FAIL(사유 없음) | `(사유 없음)` | 없음 |
| 1차 FAIL → 사람 UNCHECKED → 항목 수정(초기화) → 사람 FAIL(사유 없음) | `(사유 없음)` | 없음 |

## 사람이 Fail로 바꿀 때 남기는 사유 (G3)

- `PUT /checklist-items/{id}/cells/{part}`, `PUT /design-review-items/{id}` 요청의 선택 필드 `reason`
- 정리: 앞뒤 공백(유니코드 공백 포함)을 지우고, 빈 문자열은 null로 본다. 정리한 값이 500자(`String.length()`, UTF-16 단위)를 넘으면 `state`와 관계없이 400 `GLOBAL_4001` (D6). 정리 함수는 `checklist/support/FailReasonPolicy` 하나를 체크 셀과 디자인 항목이 함께 쓴다
- `state`가 FAIL일 때만 셀(항목)의 `reason`과 이력 `reason`에 저장한다. FAIL이 아니면 무시한다(지금처럼 null)
- 상태가 바뀌지 않을 때 (D1, 체크 셀과 디자인 항목 같은 규칙)
  - 지금 FAIL이고 FAIL을 보냈는데 사유가 있고 지금 사유와 다르면: `source = MANUAL`, 사유를 바꾸고, 이력 FAIL → FAIL 1건. 체크 셀의 `evidence`는 지금 값을 그대로 두고 이력에도 같은 값을 남긴다
  - 사유가 없거나 지금 사유와 같으면 아무것도 바꾸지 않는다(이력도 남기지 않는다)
  - FAIL이 아닌 같은 상태는 지금처럼 아무것도 바꾸지 않는다
- `reason`이 없는 옛 admin 요청도 그대로 통과한다
- 응답·이력의 `reason` 설명(Swagger, D7): 체크 셀은 "1차 판정 사유, 판정 초기화 사유, 또는 사람이 Fail로 바꿀 때 남긴 사유", 디자인 항목은 "자동 비교 사유, 판정 초기화 사유, 또는 사람이 Fail로 바꿀 때 남긴 사유"
