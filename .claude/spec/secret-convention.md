---
description: gravit-qa 환경 변수·시크릿 목록과 보관 규칙
---

# Secret Convention

gravit-qa는 서버의 `.env`(compose 프로젝트 디렉토리)에서 환경 변수로 값을 받는다. 값은 저장소에 커밋하지 않는다.
CD 워크플로를 추가하면 같은 이름을 GitHub Secrets에 둔다.

| 변수 | 용도 | 비고 |
|---|---|---|
| `QA_DB_URL`, `QA_DB_USERNAME`, `QA_DB_PASSWORD` | QA 전용 DB | 기존 Postgres 인스턴스의 별도 DB |
| `QA_AUTH_HOST` | 관리자 판정 대상 gravit-server | admin-web이 바라보는 환경과 같아야 한다 (현재 dev) |
| `QA_WORKER_ID` | 작업 점유자 식별 | compose에서 컨테이너별로 고정 |
| `FIGMA_TOKEN` | Figma PAT (읽기) | C3에서 추가 |
| `NOTION_TOKEN` | Notion Integration | gravit-chatbot과 같은 Integration. C3에서 추가 |
| `GITHUB_TOKEN` | Fine-grained PAT (public read-only) | C3에서 추가 |
| `LITELLM_QA_KEY` | litellm-gw의 gravit-qa 전용 virtual key | C3에서 추가 |
| `GRAVIT_TEST_API_KEY` | gravit-server 테스트 API 서비스 키 | #15 처리 후 추가 |

## 규칙

- 토큰 원문을 로그·DB·응답에 남기지 마라. 캐시 키가 필요하면 SHA-256 해시를 쓴다 (`AdminVerificationCache`)
- 새 변수를 추가하면 `.env.example`과 이 표를 같은 작업에서 갱신하라
