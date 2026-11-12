# 디자인 검수 (D9)

구현: `designReview/support/DesignReviewer`, `DesignReviewValidator`, `adapter/figma/support/FigmaOutlineRenderer`, `sprint/support/DesignReviewStep`

## 무엇을 비교하나

- Figma 시안이 명세에 충실하게 그려졌는지 본다. 비교 대상은 명세 "화면에 보이는 것" 행(C행)과 Figma 프레임 레이어 정보다. 구현된 화면과는 비교하지 않는다
- 이미지를 보내지 않는다. 프레임 노드 트리를 글로 바꿔 보낸다(`GET /v1/files/{key}/nodes?ids=…`, 깊이 제한 없음, 20개씩)
  - 레이어 개요: 글자(내용, 크기, 굵기, 색), 컴포넌트 이름, 이름 있는 묶음, 이미지, 프레임 기준 좌표와 크기. 위에서 아래, 왼쪽에서 오른쪽 순서
  - 숨긴 레이어와 기본 이름 묶음(`Frame 12` 등)은 줄로 남기지 않는다

## 단위와 입력

- 화면 1개당 호출 1회(WEB, APP 함께). 길이 상한(60000자)을 넘으면 플랫폼별로 나눠 호출한다
- 플랫폼은 기능 파트에 WEB, APP이 있을 때만 본다. WEB → Figma `WEB` Section, APP → `AOS` Section
- 플랫폼별 C행: `[웹]`이 있으면 WEB, `[앱]`이 있으면 APP, 둘 다 없으면 두 플랫폼 모두
- C행이 있는데 그 플랫폼 프레임이 없으면 LLM을 부르지 않고 "{WEB|AOS} 시안 프레임이 없다" 항목(UNCHECKED, 사유 `Figma 프레임 없음`)을 만든다

## 출력과 검증

- 프롬프트: `prompts/design-review-{system,user}.txt`
- 항목: `platform`, `evidence`, `specRowIds`, `title`, `expected`, `figmaNodeIds`, `reason`, `verdict`(PASS, FAIL, UNKNOWN)
- 코드 검증: 플랫폼마다 C행을 정확히 한 번씩 다룸(한 행이 두 항목에 들어가지 않음), 행 번호와 레이어 ID가 입력에 있음, PASS에는 근거 레이어 1개 이상
- 저장: 판정 PASS → PASS, FAIL → FAIL, UNKNOWN → UNCHECKED(사유 앞에 `판정 불가: `). 설명 끝에 `({화면 ID}-{행 번호들})`를 붙인다. 출처 AUTO, 이력 1건
- 검증을 3번 모두 통과하지 못하면 플랫폼마다 "디자인 검수 실패" 항목(UNCHECKED, 오류 500자까지)

## 사람 변경

- `PUT /design-review-items/{id}` `{state, version}`: MANUAL_REVIEW에서만. UNCHECKED, PASS, FAIL(NOT_APPLICABLE은 400 `DESIGN_4001`)
- 바꾸면 `source = MANUAL`, `reason = null`, 이력 1건. `GET /design-review-items/{id}/history`는 최신순 `{state, source, reason, updatedBy, updatedAt}`
