# Figma QA 페이지 해석

근거: 가이드 `02_PREP_GUIDE.md` 3-3, 적용 보고서 `06_FIGMA_QA_PAGE_APPLIED.md`(프레임 236개 매핑 표), 설계 C1-17.
구현: `adapter/figma/support/FigmaFrameParser`

## 읽는 범위

- QA 페이지(이름이 `QA`인 `CANVAS`)에서 이름이 `sprintKey`와 같은(앞뒤 공백 제외) 최상위 Section 안만 읽는다
- 같은 페이지의 `SCREEN` Section에는 화면 ID와 같은 이름의 빈 래퍼(`LG-01`, `QUIZ-02/A` 등)가 있으므로 읽지 않는다
- 화면 프레임의 하위 레이어는 읽지 않는다. 레이어 이름 규칙은 해석 대상이 아니다

## 구조 (고정 4단)

```
스프린트 Section → 플랫폼 Section(WEB / AOS) → 구역 Section → 화면 FRAME(구역 Section의 직속 자식)
```

- 구역 Section 6개: `MAIN & ONBOARDING`, `LEARNING`, `LEAGUE`, `MY`, `OTHER`, `INTERVIEW SIMULATOR`
- Section 이름은 앞뒤 공백을 빼고 대소문자까지 그대로 비교한다
- 각 단계에서 `SECTION`, `FRAME`이 아닌 노드(사각형, 그룹 등)는 무시한다

## 프레임 이름

| 항목 | 규칙 |
|---|---|
| 화면 ID | `^(?:[A-Z]+(?:-[A-Z]+)?-\d{2}(?:-\d{2})?\|[A-Z]+-[가-힣]+)$` |
| 분해 | 첫 번째 `/` 기준. 앞 = 화면 ID 후보, 뒤 = 상태. 둘 다 앞뒤 공백을 뺀다. `/`가 없으면 상태는 null. 정규식 `^(?<id>[^/]+)(?:/(?<state>.+))?$` |
| 상태 | `/`, 공백, 한글을 포함할 수 있다(`MY-04/MODAL/FOLLOW`, `AI-08/질문 재생 중`). 값 목록으로 제한하지 않는다 |
| 미확인 | 화면 ID 후보가 정확히 `미확인` → `UNCONFIRMED_FRAME`, 화면에 넣지 않는다 |
| 규칙 밖 | 분해 정규식이나 화면 ID 정규식에 맞지 않음 → `INVALID_FRAME_NAME`, 화면에 넣지 않는다 |

- `최근 응시 이력 페이지`(WEB 2개)는 `INVALID_FRAME_NAME`이 되며, 의도된 결과다. 화면으로 쓰려면 Figma에서 코드 이름을 붙인다
- `/`로 끝나거나(`LRN-02/`) `/`로 시작하는(`/A`) 이름은 분해 정규식에 맞지 않아 `INVALID_FRAME_NAME`이다

## 화면 묶음

- 화면 = (플랫폼, 화면 ID). 같은 화면 ID라도 WEB과 AOS는 별개 화면이다
- 프레임은 node ID로 구분한다. 이름과 상태가 같은 프레임도 각각 남긴다(WEB `AI-08` 12개, AOS `SETTING-01` 4개). 이름 중복은 상태 변형이 아니다
- 기본 프레임(상태 없음) 없이 상태 변형만 있는 화면도 화면이다(`ONB-01`, `QUIZ-02`)
- 프레임마다 node ID, 프레임 이름 원문, 상태, 구역 Section 이름, 폭(`absoluteBoundingBox.width`)을 남긴다
- 화면의 상태 목록(`FigmaScreenDto.states()`)은 null을 빼고, 등장 순서를 지키며, 중복을 없앤다

## 경고 (`FigmaFrameWarningType`)

| 유형 | 조건 | 처리 |
|---|---|---|
| `QA_PAGE_NOT_FOUND` | QA 페이지가 없음 | 빈 결과 |
| `SPRINT_SECTION_NOT_FOUND` | `sprintKey`와 같은 이름의 최상위 Section이 없음 | 빈 결과 |
| `UNKNOWN_SECTION` | 스프린트 Section 직속인데 `WEB`/`AOS`가 아닌 Section, 또는 플랫폼 Section 직속인데 구역 6개가 아닌 Section | 안을 읽지 않는다 |
| `FRAME_OUTSIDE_PLATFORM` | 스프린트 Section 직속 FRAME | 화면에 넣지 않는다 |
| `FRAME_OUTSIDE_AREA` | 플랫폼 Section 직속 FRAME | 화면에 넣지 않는다 |
| `NESTED_SECTION` | 구역 Section 안의 Section | 안을 읽지 않는다 |
| `UNCONFIRMED_FRAME` | 화면 ID 후보가 `미확인` | 화면에 넣지 않는다 |
| `INVALID_FRAME_NAME` | 분해 또는 화면 ID 정규식에 맞지 않음 | 화면에 넣지 않는다 |
| `PLATFORM_WIDTH_MISMATCH` | 화면 프레임 폭이 WEB 1920±2, AOS 360±2를 벗어남. 폭 값이 없어도 벗어난 것으로 본다 | 경고만, 화면에는 넣는다 |

- 경고는 순회 순서대로 쌓는다
- 경고의 `platform`은 플랫폼 Section 안에서 난 경고에만 채운다. 스프린트 Section 직속 경고는 null이다

## Figma 요청

- `GET /v1/files/{key}?depth=1`로 페이지 목록을 받고, `GET /v1/files/{key}/nodes?ids={QA 페이지}&depth=4`로 QA 페이지를 받는다
- `/nodes`의 `depth`는 요청한 노드 기준이다(1 = 직속 자식). 깊이 4면 스프린트(1), 플랫폼(2), 구역(3), 화면 프레임(4)까지 들어오고 프레임 하위 레이어는 들어오지 않는다
- 응답에서 `children`이 빠진(잘린) 플랫폼 Section과 구역 Section만 `depth=2`로 다시 받는다(최대 5회). 모르는 Section, 구역 안 Section, `SCREEN` Section은 펼치지 않는다
- 폭은 `absoluteBoundingBox`(Rectangle: `x`, `y`, `width`, `height`)에서 읽는다. `/nodes`에는 노드 속성을 거르는 파라미터가 없어 기본으로 포함된다. `size`는 `geometry=paths`일 때만 오므로 쓰지 않는다

## 검증치 (2026-10-17 적용 결과)

`FigmaClientLiveTest`가 `QA_LIVE_FIGMA_SPRINT_KEY` 스프린트에 대해 검증한다.

| 항목 | 값 |
|---|---|
| 화면에 들어간 프레임 | WEB 119, AOS 109 (합계 228) |
| 화면(플랫폼 × 화면 ID) | WEB 49, AOS 48 |
| `UNCONFIRMED_FRAME` | 6 (WEB 5, AOS 1) |
| `INVALID_FRAME_NAME` | 2 (`최근 응시 이력 페이지`) |
| 그 외 경고 | 0 |

Figma가 정리되면(미확인 해소 등) 값이 바뀐다. 그때 liveTest 기대치와 이 표를 함께 고친다.
