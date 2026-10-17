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
| Spring AI 조립 | `spring-ai-openai` 모듈로 `OpenAiApi`, `OpenAiChatModel`을 직접 조립한다. 자체 재시도는 끄고(`RetryTemplate` 1회), 오류 응답은 상태 코드와 `Retry-After`만 넘겨 본문을 노출하지 않는다 | `LlmClient`, `LlmResponseErrorHandler` |

## Figma

| 항목 | 값 |
|---|---|
| PNG 배율 | 1 |
| 내보내기 단위 | 노드 50개씩 |

## Notion

- 스프린트 `이름`이나 기능 명세 `화면 ID`가 비어 있으면 재시도해도 소용없으므로 재시도하지 않는 실패(`NonRetryableExternalApiException`)로 끝낸다

## 배포

- `docker-compose-qa.yml`의 `qa-api`에 토큰 4개를 전달한다: `FIGMA_TOKEN`, `NOTION_TOKEN`, `GITHUB_TOKEN`, `LITELLM_API_KEY`
