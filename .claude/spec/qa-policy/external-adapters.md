# 외부 연동 결정값 (C3)

근거: 설계 8(자원 격리), 10(실패·재시도 정책). C3 구현에서 정한 값이다.
코드에서 이 문서와 다른 값이 보이면 코드를 고치지 말고 차이를 보고한다.

## HTTP 재시도

| 항목 | 값 | 위치 |
|---|---|---|
| 재시도 횟수와 백오프 | 최대 3회, 1→2→4초 (`backoff-base` 1초 × 2^n) | `qa.adapter.retry`, `ExternalCallExecutor` |
| `Retry-After` | 응답에 있으면 그 값만큼 기다린다 | `RetryAfterParser` |
| 대기 상한 | 60초. 기다릴 시간이 60초를 넘으면 기다리지 않고 실패시켜 작업 큐 재시도로 넘긴다 | `qa.adapter.retry.max-wait` |

## 타임아웃

| 대상 | 값 |
|---|---|
| GitHub | 10초 |
| Notion, Figma, dev OpenAPI | 30초 |
| LLM | 120초 |

설정 위치는 `qa.adapter.{github,notion,figma,openapi,llm}.timeout`이다.

## LLM

| 항목 | 값 | 위치 |
|---|---|---|
| 429 연속 2회 | 중단(`LlmRateLimitedException`). `generate` 1회(검증 실패 재요청 포함) 안에서 센다 | `qa.adapter.llm.max-consecutive-rate-limits`, `LlmClient` |
| 분당 호출 상한 | 5회. 넘으면 1분 창이 빌 때까지 기다린다 | `qa.adapter.llm.max-requests-per-minute`, `LlmCallGuard` |
| 출력 검증 | networknt `json-schema-validator` 1.5.9, 오류 메시지 한국어(`Locale.KOREAN`) | `LlmOutputValidator` |
| 입력 상한 | 단계별 user 프롬프트 상한(반영 제안 30000, 체크리스트 30000, 디자인 검수 60000, 1차 판정 80000자) | `qa.prepare.*`, `prepare-job.md` |
| 입력 형식 | 텍스트만 보낸다. 디자인 검수도 이미지 대신 Figma 레이어를 글로 바꿔 보낸다 | `design-review.md` |
| Spring AI 조립 | `spring-ai-openai` 모듈로 `OpenAiApi`, `OpenAiChatModel`을 직접 조립한다. 자체 재시도는 끄고(`RetryTemplate` 1회), 오류 응답은 상태 코드와 `Retry-After`만 넘겨 본문을 노출하지 않는다 | `LlmClient`, `LlmResponseErrorHandler` |

## Figma

| 항목 | 값 |
|---|---|
| 프레임 레이어 조회 | `GET /v1/files/{key}/nodes?ids=…` 깊이 제한 없음, 노드 20개씩 (`FigmaClient.getFrameNodes`) |
| PNG 내보내기 | 쓰지 않는다(디자인 검수 입력을 레이어 글로 바꿨다) |

## GitHub

| 항목 | 값 |
|---|---|
| QA 기준 브랜치 | 세 저장소 공통 `qa-baseline` (`qa.adapter.github.qa-branch`). 없으면 `BranchNotFoundException`(재시도 없음) |
| 파일 목록 | `GET /repos/{owner}/{repo}/git/trees/{sha}?recursive=1`, blob만 남긴다. 잘리면(`truncated`) 경고 로그만 남기고 받은 만큼 쓴다 |
| 파일 내용 | `GET /repos/{owner}/{repo}/git/blobs/{sha}`(base64). 같은 blob은 `{storage}/repo-cache/{sha}`에서 다시 쓴다 |
| 쓰지 않게 된 기능 | compare(변경 파일), check-runs(리뷰 수집), contents(AOS versionCode) |

## Notion

- 스프린트 `이름`이나 기능 명세 `화면 ID`가 비어 있으면 재시도해도 소용없으므로 재시도하지 않는 실패(`NonRetryableExternalApiException`)로 끝낸다
- 기능 명세 DB 속성은 `화면 ID`, `화면 이름`, `최종 검증일`만 읽는다. 명세 상태, 파트, 라우트, 관련 API, Figma, FE 스펙 ID는 본문 맨 위 `항목 | 값` 표에서 읽는다(`prepare-job.md`의 SPEC_COLLECT)
- 쓰기는 이번 스프린트 `대상 기능` 페이지의 본문과 수정 이력 `반영일` 칸만 허용한다. 범위와 허용 요청은 `reflection.md`의 Notion 쓰기 허용 범위. 스프린트 페이지 `QA 기준점` 속성은 더 이상 쓰지 않는다
- 블록 쓰기: `PATCH /v1/blocks/{id}`(글, 표 행), `PATCH /v1/blocks/{id}/children`(`after` 지정 추가), `DELETE /v1/blocks/{id}`. API 버전 `2022-06-28`

## 배포

- `docker-compose-qa.yml`의 `qa-api`에 토큰 4개를 전달한다: `FIGMA_TOKEN`, `NOTION_TOKEN`, `GITHUB_TOKEN`, `LITELLM_API_KEY`
