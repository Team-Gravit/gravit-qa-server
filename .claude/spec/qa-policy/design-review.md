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

- `PUT /design-review-items/{id}` `{state, version, reason?}`: MANUAL_REVIEW에서만. UNCHECKED, PASS, FAIL, NOT_APPLICABLE
  - NOT_APPLICABLE(해당 없음)은 사람만 고른다. Figma 프레임이 없는 항목처럼 검수 대상이 아닐 때 쓴다
  - 체크 셀과 달리 해당 없음 항목도 다시 바꿀 수 있다(`editable`은 MANUAL_REVIEW면 true)
  - `reason`은 체크 셀과 같은 규칙: 앞뒤 공백을 지우고 빈 문자열은 null, 500자를 넘으면 400 `GLOBAL_4001`, FAIL일 때만 저장(`fix-report.md` G3)
- 바꾸면 `source = MANUAL`, `reason`은 FAIL이면 요청 사유(없으면 null), 그 밖에는 null, 이력 1건. `GET /design-review-items/{id}/history`는 최신순 `{state, source, reason, updatedBy, updatedAt}`
- 상태가 같으면 바꾸지 않는다. 예외: 지금 FAIL인 항목에 FAIL과 다른 사유를 보내면 `source = MANUAL`, 사유를 바꾸고 이력 FAIL → FAIL 1건(D1)
- 진행도 DESIGN의 `total`과 화면 `completionRate`의 분모에서 NOT_APPLICABLE을 뺀다. 종료 조건은 UNCHECKED 0이라 해당 없음은 종료를 막지 않는다

## 항목 수정, 삭제

- MANUAL_REVIEW이고 실행 중인 작업이 없을 때만(아니면 409 `SPRINT_4092`). 체크 항목 편집과 달리 DESIGN_REVIEW, AUTO_REVIEW에서는 안 된다
- `PATCH /design-review-items/{id}` `{title?, expected?, version}`: 전달한 필드만 바꾼다(앞뒤 공백 제거 후 비교)
  - 행위나 설명이 바뀌면 상태와 관계없이 UNCHECKED, `source = MANUAL`, `reason = "항목 수정으로 판정 초기화"`로 되돌리고 초기화 기록 1건. 해당 없음이던 항목도 UNCHECKED가 된다
  - 이미 사람이 둔 UNCHECKED여도 UNCHECKED → UNCHECKED 기록을 남긴다. 수정사항 파일이 사유를 고를 때 이 기록을 경계로 쓴다(`fix-report.md`)
  - 같은 값을 보내 바뀐 것이 없으면 기록을 남기지 않는다
- `DELETE /design-review-items/{id}?version=` → 204. 이력도 지워진다(FK `ON DELETE CASCADE`). `version`이 없으면 400 `GLOBAL_4001`
- 낙관적 잠금: `version`이 다르면 409 `GLOBAL_4091`
