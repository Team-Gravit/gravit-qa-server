# gravit-qa-server

Gravit QA 플랫폼(서비스명 gravit-qa) 백엔드. 백오피스(gravit-admin-web) QA 메뉴의 API와 QA 준비 작업(명세 로드, 수정 이력 반영, 체크리스트 생성, 디자인 검수, 1차 자동화 검수)을 담당한다.

- 설계: [`docs/design/03_DESIGN.md`](docs/design/03_DESIGN.md)
- API 계약: [`docs/api/openapi.yaml`](docs/api/openapi.yaml)
- QA 흐름 정책: [`.claude/spec/qa-policy/`](.claude/spec/qa-policy/README.md)
- 개발 하네스: [`.claude/CLAUDE.md`](.claude/CLAUDE.md)

## 현재 범위 (QA 흐름 재설계, 2026-11)

| 구성 | 상태 |
|---|---|
| 관리자 인증: `/admin/me` 판정 필터 + 성공 300초 캐시 | 완료 |
| 작업 큐: 등록·점유(`SKIP LOCKED`)·단계 기록·취소·재시도(2→4→8분)·heartbeat 회수 | 완료 |
| 명세 로드(`SPEC_LOAD`): 연결(Notion, Figma, 세 저장소 `qa-baseline`), 명세 수집, 수정 이력 반영 제안 | 완료 |
| 반영 승인·거절(Notion 본문, `반영일` 기록) | 완료 |
| QA 준비(`QA_PREPARE`): 체크리스트 생성, 디자인 검수, 1차 자동화 검수(코드 읽기) | 완료 |
| 1차·2차 체크리스트, 디자인 검수, 셀·항목 편집, 종료 API | 완료 |
| 자동 실행(Playwright)·증적·리뷰 수집·페르소나 | 없앰(D13) |

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
