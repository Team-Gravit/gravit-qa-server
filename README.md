# gravit-qa-server

Gravit QA 플랫폼(서비스명 gravit-qa) 백엔드. 백오피스(gravit-admin-web) QA 메뉴의 API와 QA 작업(준비·자동 실행·리뷰 수집)을 담당한다.

- 설계: [`docs/design/03_DESIGN.md`](docs/design/03_DESIGN.md)
- API 계약(OpenAPI 초안): [`docs/api/openapi.yaml`](docs/api/openapi.yaml)
- 개발 하네스: [`.claude/CLAUDE.md`](.claude/CLAUDE.md)

## 현재 범위 (C2 골격)

| 구성 | 상태 |
|---|---|
| Flyway 스키마 `V1` (설계 4절 전체 테이블) | 완료 |
| 관리자 인증: `/admin/me` 판정 필터 + 성공 300초 캐시, 200/401/403/503 매핑 | 완료 |
| 작업 큐: 등록·점유(`SKIP LOCKED`)·단계 기록·취소·재시도(2→4→8분)·heartbeat 회수 | 완료 |
| `GET /api/v1/jobs/{id}`, `POST /api/v1/jobs/{id}/cancel` | 완료 |
| 외부 수집 어댑터: GitHub, Notion, Figma, dev OpenAPI, LLM(litellm-gw) + 공통 재시도 규칙 | 완료 (C3) |
| 스냅샷 파일 저장소, AOS 버전 파서 | 완료 (C3) |
| 스프린트·체크리스트·실행·증적·페르소나 API, 셋업 작업 처리기 | C4 이후 |

## 로컬 실행

```bash
cp .env.example .env   # 값 채우기
export $(grep -v '^#' .env | xargs)
./gradlew bootRun --args='--spring.profiles.active=api'
```

## 테스트

```bash
./gradlew test                       # Docker가 있으면 Testcontainers로 Postgres 기동
QA_TEST_DB_URL=jdbc:postgresql://localhost:5432/gravit_qa_test \
QA_TEST_DB_USERNAME=postgres QA_TEST_DB_PASSWORD=... ./gradlew test   # 기존 DB 사용
./gradlew liveTest                   # 실제 Notion, Figma 호출 스모크(@Tag("live")). 변수가 없으면 건너뜀
```

기본 `test`는 외부 서비스를 호출하지 않는다. `liveTest`에 필요한 변수는 `.claude/spec/secret-convention.md`를 본다.

## 배포

`docker-compose-qa.yml` 하나로 같은 이미지를 `api` / `e2e` 프로필 두 컨테이너로 띄운다. 두 컨테이너 모두 자원 한도(`deploy.resources.limits`)가 필수다.
`llm-net`(external) 네트워크에 참여한다.

- CD(`.github/workflows/cd.yml`): dev push 시 CI → 이미지 빌드(`latest`, 커밋 SHA 태그) → Docker Hub push → 서버 `/home/serverking/gravit/qa`에서 qa-api만 갱신하고 healthy를 기다린다. qa-e2e는 C7 전까지 올리지 않는다
- 서버 `.env`는 사람이 관리하고 CD는 건드리지 않는다
- 서버 준비, 첫 배포, 스모크 확인, 되돌리기: [`docs/deploy-runbook.md`](docs/deploy-runbook.md)
