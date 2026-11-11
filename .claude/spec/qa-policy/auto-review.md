# 1차 자동화 검수 (코드 읽기 판정)

구현: `autoReview/support/CodeSelector`, `ServerCodeSelector`, `WebCodeSelector`, `AppCodeSelector`, `CodeChunker`, `AutoReviewer`, `RepoSourceReader`, `sprint/support/AutoReviewStep`

## 원칙

- 저장소 테스트를 실행하지 않는다. CONNECT 단계에서 잡은 `qa-baseline` SHA의 코드만 읽는다(세 저장소는 읽기만 한다)
- 파일 목록은 Git Trees API(재귀) 1회, 내용은 Blobs API. 같은 blob은 `repo-cache/{blob SHA}`에서 다시 쓴다. 200KB가 넘는 파일은 읽지 않는다

## 단위

- 화면 × 파트. 그 파트가 들어 있는 AUTO 항목을 한 번에 판정한다
- 고른 파일이 없으면 LLM을 부르지 않고 셀을 UNCHECKED로 두며 사유 `판정 불가: 관련 코드를 찾지 못했습니다(…)`

## 파일 고르기

| 파트 | 저장소 | 규칙 |
|---|---|---|
| SERVER | gravit-server | 관련 API `메서드 URI`와 `@RequestMapping`(클래스) + `@Get/Post/Put/Patch/DeleteMapping`(메서드)을 합친 경로가 같은 `*Controller.java` → 그 컨트롤러가 주입받는 Service, Facade, Validator, Repository 등과 참조하는 Request/Response/Dto(깊이 1) → 그 서비스들이 주입받는 타입(깊이 2). `*Docs.java` 제외. 경로 변수 이름과 끝 `/`는 무시 |
| WEB | gravit-web | 명세 라우트(`,`로 여러 개)와 `apps/web/src/app/routes/**` 파일 라우트를 맞춘다(`_` 세그먼트, `index`, `route` 제거, `.`은 `/`, `$이름`은 경로 변수, `-`로 시작하는 파일 제외) → `@/`, 상대 경로 import를 깊이 3까지. `shared/ui/**`, 테스트, 스토리 제외 |
| APP | gravit-aos + gravit-web `apps/native` | 관련 API와 경로가 같은 Retrofit 메서드(`@GET("…")` 등) → 그 메서드를 부르는 파일 → 그 파일이 선언한 클래스를 쓰는 파일. API 정의 파일은 해당 메서드 주변 10줄만 넣는다. 관련 API에 `/oauth`가 있거나 항목에 로그인, 뒤로, 딥링크, WebView가 나오면 `apps/native/app`, `apps/native/bridge`도 넣는다 |

- 깊이 순서, 같은 깊이는 경로 순서로 놓는다

## 나눠 담기와 판정

- 코드 예산 = 단계 상한(80000자) - 고정부(시스템 제외 user 프롬프트) - 500. 파일을 순서대로 담고, 예산을 넘는 파일은 줄 범위로 나눈다. 최대 4번 호출, 넘는 파일은 `droppedFiles`
- 코드에는 줄 번호를 붙인다(`### {저장소}/{경로} (시작-끝행)`, `   12| …`)
- 프롬프트: `prompts/auto-review-{system,user}.txt`. 출력: `results[{itemId, evidence[{path, startLine, endLine}], reason, verdict}]`
- 코드 검증: 단위의 항목을 정확히 한 번씩 판정, PASS·FAIL에는 근거 1개 이상, 근거 경로와 줄 범위가 보낸 코드 안
- 여러 번 나눠 호출했으면 "근거 있는 FAIL > PASS > 판정 불가" 순서로 합친다. 검증을 3번 모두 통과하지 못한 부분은 판정 불가로 본다
- 저장: PASS, FAIL은 그 상태로, 판정 불가는 UNCHECKED. 출처 AUTO, 사유 `{이유} (근거 {경로}:{시작}-{끝})`, 나눠 호출했으면 `(코드 n/m부분)`
