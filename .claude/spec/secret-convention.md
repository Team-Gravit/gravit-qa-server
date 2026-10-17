---
description: gravit-qa 환경 변수·시크릿 목록과 보관 규칙
---

# Secret Convention

gravit-qa는 서버의 `.env`(compose 프로젝트 디렉토리 `/home/serverking/gravit/qa`)에서 환경 변수로 값을 받는다. 값은 저장소에 커밋하지 않는다.
앱 변수는 서버 `.env`에만 둔다. CD(`cd.yml`)는 `.env`를 읽지도 쓰지도 않고 앱 변수를 넘기지 않는다. 그래서 아래 앱 변수는 GitHub Secrets에 두지 않는다.

| 변수 | 용도 | 비고 |
|---|---|---|
| `QA_DB_URL`, `QA_DB_USERNAME`, `QA_DB_PASSWORD` | QA 전용 DB | 기존 Postgres 인스턴스의 별도 DB |
| `QA_AUTH_HOST` | 관리자 판정 대상 gravit-server | admin-web이 바라보는 환경과 같아야 한다 (현재 dev) |
| `QA_WORKER_ID` | 작업 점유자 식별 | compose에서 컨테이너별로 고정 |
| `FIGMA_TOKEN` | Figma PAT (파일 내용·이미지 읽기) | qa-api만. 발급 계정이 QA 대상 파일을 볼 수 있어야 한다 |
| `NOTION_TOKEN` | Notion Integration | gravit-chatbot과 같은 Integration. QA 상위 페이지에 연결해야 읽힌다. qa-api만 |
| `GITHUB_TOKEN` | Fine-grained PAT (public read-only) | 호출 한도용(인증 시 시간당 5,000회). 비어 있으면 비인증 호출. qa-api만 |
| `LITELLM_API_KEY` | litellm-gw 키 | 게이트웨이에 DB가 없어 virtual key를 만들 수 없으므로 기존 master key를 쓴다 (설계 7-3, 02_PREP_GUIDE 1-4). 비어 있으면 LLM 호출이 재시도 불가 실패. qa-api만 |
| `GRAVIT_TEST_API_KEY` | gravit-server 테스트 API 서비스 키 | #15 처리 후 추가 |

외부 연동 base-url과 DB ID 같은 비밀이 아닌 값은 `application.yml`의 `qa.adapter.*`에 둔다.

### compose 보간 (서버 `.env`, 비밀 아님)

`docker-compose-qa.yml`을 해석할 때 쓴다. 컨테이너에는 넘기지 않는다.

| 변수 | 용도 | 비고 |
|---|---|---|
| `DOCKER_HUB_USERNAME` | 이미지 이름 `{값}/gravit-qa-server` | CD는 셸 환경 변수로 넘기고(셸 값이 `.env`보다 우선), `.env` 값은 서버에서 compose를 직접 돌릴 때 쓴다 |
| `QA_API_PORT` | qa-api 호스트 포트 | |
| `QA_API_CPUS`, `QA_API_MEMORY` | qa-api 자원 한도 | |
| `QA_E2E_CPUS`, `QA_E2E_MEMORY` | qa-e2e 자원 한도 | qa-e2e를 띄우지 않아도 비어 있으면 compose 해석이 실패한다 |

### CD 전용 (GitHub Secrets)

gravit-server CD와 같은 이름을 쓴다. 저장소 시크릿 또는 이 저장소에 접근을 허용한 조직 시크릿이다.

| 변수 | 용도 |
|---|---|
| `DOCKER_HUB_USERNAME`, `DOCKER_HUB_PASSWORD` | 이미지 push |
| `APPCENTER_SERVER_IP`, `APPCENTER_SERVER_PORT`, `APPCENTER_SERVER_USERNAME`, `APPCENTER_SERVER_PASSWORD` | 앱센터 서버 scp, ssh |

### 스모크 테스트 전용 (서버 `.env`에는 두지 않는다)

`./gradlew liveTest`가 실제 서비스를 호출할 때만 쓴다. 없으면 해당 테스트를 건너뛴다.

| 변수 | 용도 |
|---|---|
| `QA_LIVE_NOTION_SPRINT_DB_ID`, `QA_LIVE_NOTION_SPEC_DB_ID` | Notion 스프린트 DB, 기능 명세 DB ID (+ `NOTION_TOKEN`) |
| `QA_LIVE_FIGMA_FILE_KEY` | QA 페이지가 있는 Figma 파일 키 (+ `FIGMA_TOKEN`) |

## 규칙

- 토큰 원문을 로그·DB·응답에 남기지 마라. 캐시 키가 필요하면 SHA-256 해시를 쓴다 (`AdminVerificationCache`)
- 외부 연동 예외 메시지에는 상태 코드와 경로만 남기고 응답 본문을 넣지 마라. LiteLLM 인증 오류는 마스킹된 키를 되돌려 준다
- 토큰을 담는 `*Props` record는 `toString()`에서 토큰을 빼라
- 새 변수를 추가하면 `.env.example`과 이 표를 같은 작업에서 갱신하라
