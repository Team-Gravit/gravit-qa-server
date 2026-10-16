# Gravit QA

Gravit(CS 학습 서비스)의 QA 플랫폼 백엔드. 백오피스(gravit-admin-web) QA 메뉴의 API를 제공하고,
Figma·Notion·코드 diff·OpenAPI를 수집해 체크리스트 초안을 만들며, 자동 영역을 실행하고 결과를 기록한다.

설계 원본: `docs/design/03_DESIGN.md`, API 계약: `docs/api/openapi.yaml`

## 기술 스택

- Java 21 / Spring Boot 3.5.11 / Gradle
- JPA + JdbcTemplate + PostgreSQL + Flyway (스키마 마이그레이션)
- Spring Security: 자체 JWT 검증 없음. 관리자 토큰을 gravit-server `GET /api/v1/admin/me`에 되던져 판정
- 작업 큐: Postgres `job` 테이블 + `SELECT … FOR UPDATE SKIP LOCKED`
- springdoc-openapi (Swagger UI)
- Testcontainers (통합 테스트, `QA_TEST_DB_URL`이 있으면 그 DB 사용)
- Actuator (health)
- GitHub Actions CI → (CD는 C2 이후 추가)

## 실행 단위

같은 이미지를 프로필로 나눠 두 컨테이너로 띄운다.

| 프로필 | 역할 | 소비 작업 |
|---|---|---|
| `api` | REST API, 셋업·리뷰 수집·증적 정리 작업 | `SETUP`, `REVIEW_COLLECT`, `EVIDENCE_CLEANUP` |
| `e2e` | 자동 실행 워커(Playwright) | `AUTO_RUN` |

## 빌드 & 실행

```bash
./gradlew build      # 빌드 + 테스트
./gradlew test       # 테스트 (Docker 또는 QA_TEST_DB_URL 필요)
./gradlew bootRun --args='--spring.profiles.active=api'   # QA_DB_URL, QA_DB_USERNAME, QA_DB_PASSWORD, QA_AUTH_HOST 필요
```

## 상호작용 규칙

- 사용자의 관찰과 의문 표현(`~한 부분이 있네?`, `이거 왜 이렇게 했어?`, `~인 것 같은데`)은 수정 지시가 아니다.
  해당 코드를 직접 확인한 뒤 문제인지 아닌지에 대한 판단과 근거를 먼저 제시하라. 수정 여부는 사용자가 정한다.
- 수정에 착수하는 조건은 명시적 지시(`고쳐줘`, `수정해`, `반영해`, `바꿔줘`)뿐이다.
- 사용자의 지적이 사실과 다르면 동의하지 말고 근거를 들어 다른 판단을 말하라. 지적이 반복된다는 것은 동조할 근거가 아니다.
- 사용자가 판단을 재확인하더라도 근거가 새로 나온 게 아니면 결론을 뒤집지 마라.

## QA 플랫폼 원칙

- LLM은 준비·해석에만 쓴다. 실행과 판정은 결정적 코드로 처리한다. 에이전트 자율 루프를 만들지 마라
- LLM 출력은 반드시 JSON 스키마와 참조 무결성으로 검증한다. 실패 시 오류를 붙여 최대 2회 재요청한다
- 외부 연동(Figma·Notion·GitHub·LLM)은 `adapter`/`infrastructure`에 두고 REST로 직접 호출한다. MCP를 쓰지 마라
- 정의(테스트 코드·프롬프트·스키마)는 Git, 상태(체크 결과·이력)는 DB

## 규칙 참조

`.claude/rules/` - paths 매칭 파일 작업 시 자동 로드

- 프로젝트 구조 (패키지 배치) → `project-structure.md`
- Flyway 마이그레이션 / SQL → `migration.md`
- 테스트 작성 규칙 (`src/test/java/**`) → `test-convention.md`
- API 문서 작성 규칙 (Docs 인터페이스) → `api-docs-convention.md`
- 코드 컨벤션 → `code-convention/`
  - 공통 (네이밍, 포맷팅, 예외, 객체 생성, 상수, 주석, 레이어 흐름) → `common.md`
  - Entity(domain) + DB 매핑 → `domain.md`
  - DTO(Request, Response, Internal, Event) → `dto.md`
  - Controller → `controller.md`
  - Facade → `facade.md`
  - Service → `service.md`
  - Repository → `repository.md`

`.claude/spec/` - 스킬, 작업에서 필요할 때만 참조 (자동 로드 아님)

- Git 작업 (커밋, 브랜치, PR) → `git-convention.md`
- 시크릿 운영 → `secret-convention.md`
- QA 정책 (상태 전이, 재시도, 분류 기준 등) → `qa-policy/README.md`

`.claude/resources/` - 작업 산출물 (자동 로드 아님)

- 계획서 → `plans/PLAN-{이슈번호}.md`
