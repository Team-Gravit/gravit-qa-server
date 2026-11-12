# [PLAN-11] QA 서버 dev 자동 배포와 배포 절차 정리

> 이슈: #11
> 브랜치: cicd/11-dev-deploy

## 목표
dev push 시 CI가 성공한 뒤 이미지를 빌드해 Docker Hub에 올리고 앱센터 서버의 qa-api만 갱신하는 CD를 만든다. 이때 서버 `.env`(외부 연동 비밀값)는 건드리지 않는다.
사람이 할 서버 작업 순서와 배포 후 스모크 확인 절차를 runbook으로 남긴다. qa-e2e 기동(C7), 서버 작업 실행, 비밀값 입력은 범위 밖이다.
근거: gravit-server `.github/workflows/cd-dev.yml`, 설계 `03_DESIGN.md` 3, 7-2, 8절, 가이드 `02_PREP_GUIDE.md` Part 2. `docs/`는 세션 시작 직전(2026-10-17 18:47~18:49)에 갱신된 로컬본 기준.

## 배포 전 점검 결과 (코드 확인)

| # | 항목 | 결과 | 근거 |
|---|---|---|---|
| 1 | 환경 변수 목록 일치 | **일치.** `application.yml` 필수(기본값 없음) `QA_DB_URL`, `QA_DB_USERNAME`, `QA_DB_PASSWORD`, `QA_AUTH_HOST` / 선택(기본 빈 값) `QA_WORKER_ID`, `GITHUB_TOKEN`, `NOTION_TOKEN`, `FIGMA_TOKEN`, `LITELLM_API_KEY`. compose qa-api `environment`는 이 9개 + `TZ`, `SPRING_PROFILES_ACTIVE`(`QA_WORKER_ID`는 compose에서 `qa-api`로 고정). `.env.example`은 앱 변수 8개(`QA_WORKER_ID` 제외) + compose 보간 변수 6개 | `application.yml`, `docker-compose-qa.yml`, `.env.example` |
| 1-a | **발견: `QA_E2E_*`도 필수** | qa-e2e를 띄우지 않아도 `QA_E2E_CPUS`, `QA_E2E_MEMORY`가 비면 compose가 파일 전체를 해석하지 못해 `pull qa-api`부터 실패한다. 브리프의 `.env` 추가 목록에 없다 → 결정 D2 | 로컬 Compose v2.39.4 재현: `'services[qa-e2e].deploy.resources.limits.cpus' strconv.ParseFloat: parsing "": invalid syntax` |
| 2 | api 워커 작업 유형 | `api` 프로필 `job-types`는 `SETUP`, `REVIEW_COLLECT`, `EVIDENCE_CLEANUP`. `JobRunner.resolveConsumableTypes`가 처리기 없는 유형을 경고 후 제외하고 처리기는 `SetupJobHandler`뿐이라 **실제 소비 유형은 `[SETUP]`** | `application-api.yml`, `JobRunner.java`, `sprint/handler/SetupJobHandler.java` |
| 2-a | 비어 있을 때 | **아무것도 집지 않는다**(모든 유형을 집지 않음). `claimNext`가 `types.isEmpty()`면 `Optional.empty()`, SQL도 `type IN (:types)` | `JobQueueRepositoryImpl.claimNext`, `JobQueueSql.CLAIM_NEXT` |
| 2-b | qa-e2e 없는 동안 `AUTO_RUN` | api 워커는 집지 않는다(목록에 없음). `AUTO_RUN`은 `POST /checklist/confirm`에서 `shouldStartAutoRun=true`이고 확정 AUTO 항목이 있을 때 생긴다. C7 전에는 처리기가 없어(e2e 프로필도 동일) `QUEUED`로 남고, 남아 있는 동안 스프린트 종료가 `SPRINT_4094`로 막힌다 → runbook에 주의로 적는다 | `SprintFacade.confirmChecklist`, `SprintFacade.closeSprint` |
| 3 | 헬스 체크 | **인증 없이 열려 있다.** `exposure.include: health`, `SecurityConfig`가 `/actuator/health`, `/actuator/health/**` permitAll, `AdminTokenAuthenticationFilter.shouldNotFilter`가 `/api/` 밖을 건너뜀. `show-details` 기본 `never`라 본문은 `{"status":"UP"}`뿐. 기본 지표에 DataSource가 있어 DB 연결이 끊기면 503 `DOWN` | `application.yml`, `SecurityConfig.java`, `AdminTokenAuthenticationFilter.java` |
| 3-a | compose healthcheck | **둔다**(구현 계획 3). 이미지에 curl이 있다 | `eclipse-temurin:21-jre` 실행 확인: Ubuntu 26.04, `/usr/bin/curl` |
| 4 | 증적 볼륨 쓰기 권한 | **쓸 수 있다.** Dockerfile에 `USER`가 없어 root(uid 0)로 실행되고, named volume `gravit-qa-evidence`는 root 소유로 생성된다. 하위 경로는 `SnapshotFileStore`가 `Files.createDirectories`로 만든다. root 실행은 하드닝 대상이나 범위 밖 | `Dockerfile`, 이미지 `id -u` = 0, `SnapshotFileStore.java:70` |
| 5 | Flyway 빈 DB 적용 | **적용된다.** `QaSpringBootTest` → `PostgresTestConfig`가 새 `postgres:16-alpine` 컨테이너를 띄우고 `flyway.enabled=true`로 V1, V2를 적용한 뒤 `ddl-auto: validate`로 엔티티를 검증한다. CI `./gradlew build`가 같은 테스트를 돈다. 서버 Postgres 버전은 [확인 필요](테스트는 16 기준, SQL은 JSONB 등 일반 기능만 사용) | `src/test/java/gravit/qa/support/PostgresTestConfig.java`, `QaSpringBootTest.java` |
| 6 | **발견: 서버 Compose 명령** | gravit-server CD는 `docker-compose`(하이픈)를 쓴다. 서버에 `docker compose` 플러그인이 있는지는 모른다. Compose v1이면 `deploy.resources.limits`가 적용되지 않아 "자원 한도 필수"(설계 8절)가 조용히 깨진다 → CD가 v2를 확인하고 아니면 중단(구현 계획 1) | gravit-server `cd-dev.yml` |
| 7 | `.env` 해석 방식 검증 | 셸 환경 변수가 `.env`보다 우선해 보간되고, 값 없는 `environment` 항목(`- FIGMA_TOKEN`)은 `.env`에서 채워진다 | 로컬 `docker compose config`로 확인 (`DOCKER_HUB_USERNAME=fromshell`이 `.env`의 값을 이김) |

## 확정한 선택 (브리프가 계획서에 맡긴 항목)

- **`.env` 보존: 셸 환경 변수로 넘긴다.** `appleboy/ssh-action`의 `envs: DOCKER_HUB_USERNAME`으로 원격 셸에 export하면 compose 보간에서 `.env`보다 우선한다(점검 7). CD는 `.env`를 읽지도 쓰지도 않는다.
  - 고른 이유: `.env`에 키를 덧붙이는 방식은 (1) 600 파일을 CD가 수정하고, (2) 마지막 줄에 개행이 없으면 직전 키를 깨뜨리며, (3) 값이 바뀌어도 "없을 때만 추가"라 갱신되지 않는다. 셸 변수 방식은 이 세 위험이 없고 매 배포마다 GitHub Secret 값이 그대로 쓰인다.
  - `.env`의 `DOCKER_HUB_USERNAME`은 서버에서 사람이 compose를 직접 돌릴 때(로그 확인, 되돌리기)를 위해 runbook에서 넣는다. CD는 이 값에 의존하지 않는다.
- **CI 연동: `needs` + 재사용 워크플로.** `ci.yml`에 `workflow_call`을 추가하고 `cd.yml`의 첫 job이 이를 호출, 빌드와 배포가 `needs`로 잇는다.
  - `workflow_run`을 고르지 않은 이유: `workflow_run`은 **기본 브랜치(main)에 있는 워크플로 파일로만** 트리거된다(이 저장소 기본 브랜치는 `main`). dev에 `cd.yml`을 머지해도 main에 반영될 때까지 CD가 돌지 않고, 이후에도 dev에서 고친 `cd.yml`이 반영되지 않는다.
  - 중복 실행 방지: `ci.yml`의 `push` 대상에서 `dev`를 빼고(`main`만 유지) dev push의 테스트는 CD 안의 CI job이 맡는다. PR CI(`pull_request: [dev, main]`)는 그대로다.
- **compose healthcheck를 두고 CD가 healthy를 기다린다.** 기동 실패(DB 접속, Flyway, 필수 변수 누락)를 배포 job 실패로 드러낸다. 저장소가 **public**이라 Actions 로그가 공개되므로 실패 시 컨테이너 로그는 출력하지 않고 상태만 남긴다.
- **이미지 이름**: `{DOCKER_HUB_USERNAME}/gravit-qa-server` (가이드 Part 2 #14 값). 태그 `latest`, `${{ github.sha }}`(40자).

## 영향 범위
### 신규 파일
- `.github/workflows/cd.yml` — dev push(또는 수동 실행) 시 CI → 이미지 빌드, push → 서버 배포
- `docs/deploy-runbook.md` — 사람이 할 서버 작업 순서, 스모크 확인, 되돌리기 (D1: git 추적)

### 수정 파일
- `.github/workflows/ci.yml` — `workflow_call` 추가, `push` 대상에서 `dev` 제거
- `docker-compose-qa.yml` — qa-api에 `healthcheck`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`(D3) 추가
- `.env.example` — `DOCKER_HUB_USERNAME`, `QA_E2E_*`(D2) 주석 보강
- `.claude/spec/secret-convention.md` — **정책 변경.** "CD 워크플로를 추가하면 같은 이름을 GitHub Secrets에 둔다"를 "앱 변수는 서버 `.env`에만 두고 CD는 넘기지 않는다"로 바꾸고, CD 전용 GitHub Secrets 목록과 compose 보간 변수 목록을 추가
- `README.md` — "배포" 절에 CD 흐름, runbook 위치, 되돌리기 한 줄
- `.gitignore` — `/docs/` → `/docs/*` + `!/docs/deploy-runbook.md` (D1)
- (로컬, 추적 안 함) `docs/api/openapi.yaml` — 설계 문서 정정(구현 계획 7)

## 구현 계획
> Java 코드, Flyway 변경 없음. Entity, Repository, Service, Facade, DTO, Controller 해당 없음(Facade 불필요).

1. **`.github/workflows/cd.yml` (신규)**
   - `name: CD`, `on: push: branches: [dev]` + `workflow_dispatch`
   - `concurrency: { group: cd-dev, cancel-in-progress: false }` — 배포를 한 번에 하나씩
   - `permissions: contents: read`
   - job `ci`: `if: github.ref == 'refs/heads/dev'`, `uses: ./.github/workflows/ci.yml` (dev 외 브랜치에서 수동 실행하면 이후 job이 모두 건너뛰어짐)
   - job `build` (`needs: ci`, `env: IMAGE: ${{ secrets.DOCKER_HUB_USERNAME }}/gravit-qa-server`)
     - `actions/checkout@v4` → `actions/setup-java@v4`(21, temurin) → `gradle/actions/setup-gradle@v4`
     - `./gradlew bootJar` — 테스트 재실행 없음. 산출물 `build/libs/gravit-qa-server-0.0.1-SNAPSHOT.jar`(Dockerfile `JAR_NAME`과 일치, `rootProject.name = 'gravit-qa-server'`)
     - `docker/login-action@v3` (`DOCKER_HUB_USERNAME`, `DOCKER_HUB_PASSWORD`)
     - `docker build -t $IMAGE:latest -t $IMAGE:${{ github.sha }} .` → `docker push --all-tags $IMAGE`
   - job `deploy` (`needs: build`)
     - `actions/checkout@v4`
     - `appleboy/scp-action@v1.0.0`: `source: "docker-compose-qa.yml"`, `target: "/home/serverking/gravit/qa"`, 시크릿 `APPCENTER_SERVER_IP/USERNAME/PASSWORD/PORT`. **`rm` 옵션을 쓰지 않는다**(대상 디렉터리를 지워 `.env`가 사라짐)
     - `appleboy/ssh-action@v1.2.5`: `envs: DOCKER_HUB_USERNAME`, 스크립트:
       ```sh
       set -eu
       cd /home/serverking/gravit/qa

       # .env는 사람이 관리한다(외부 연동 비밀값). CD는 읽지도 쓰지도 않는다.
       if [ ! -f .env ]; then echo ".env가 없습니다. runbook 2단계를 먼저 하세요."; exit 1; fi

       # deploy.resources.limits는 Compose v2에서만 적용된다.
       if docker compose version >/dev/null 2>&1; then COMPOSE="docker compose"
       elif docker-compose version --short 2>/dev/null | grep -q '^2'; then COMPOSE="docker-compose"
       else echo "Compose v2가 필요합니다."; exit 1; fi

       docker network inspect llm-net >/dev/null 2>&1 || docker network create llm-net

       $COMPOSE -f docker-compose-qa.yml pull qa-api
       $COMPOSE -f docker-compose-qa.yml up -d --no-deps --force-recreate qa-api

       # 저장소가 public이라 로그는 남기지 않는다. 원인은 서버에서 docker logs로 본다.
       i=0
       until [ "$(docker inspect -f '{{.State.Health.Status}}' gravit-qa-api)" = healthy ]; do
         i=$((i + 1))
         if [ "$i" -ge 36 ]; then echo "qa-api가 180초 안에 healthy가 되지 않았습니다."; exit 1; fi
         sleep 5
       done

       docker image prune -f
       ```
     - qa-e2e는 pull, up 하지 않는다

2. **`.github/workflows/ci.yml` (수정)**
   - `on.push.branches: [dev, main]` → `[main]`
   - `on.workflow_call:` 추가
   - job 본문은 그대로

3. **`docker-compose-qa.yml` (수정, qa-api만)**
   ```yaml
   healthcheck:
     test: ["CMD", "curl", "-fsS", "-o", "/dev/null", "http://localhost:8080/actuator/health"]
     interval: 10s
     timeout: 5s
     retries: 3
     start_period: 90s
   ```
   - `environment`에 `- JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` 추가 (D3)
   - qa-e2e는 손대지 않는다(C7)

4. **`.env.example` (수정)**
   - `DOCKER_HUB_USERNAME` 위 주석: CD는 셸 변수로 넘기고, 이 값은 서버에서 compose를 직접 돌릴 때 쓴다
   - `QA_E2E_*` 위 주석(D2): qa-e2e를 띄우지 않아도 compose 해석에 필요

5. **`.claude/spec/secret-convention.md` (수정, 정책 변경)**
   - 상단 문장 교체: 앱 변수는 서버 `.env`에만 둔다. CD는 `.env`를 쓰지 않고 앱 변수를 넘기지 않는다
   - 새 절 "CD 전용 (GitHub Secrets)": `DOCKER_HUB_USERNAME`, `DOCKER_HUB_PASSWORD`, `APPCENTER_SERVER_IP`, `APPCENTER_SERVER_USERNAME`, `APPCENTER_SERVER_PASSWORD`, `APPCENTER_SERVER_PORT` (gravit-server와 같은 이름)
   - 새 절 "compose 보간 (서버 `.env`, 비밀 아님)": `DOCKER_HUB_USERNAME`, `QA_API_PORT`, `QA_API_CPUS`, `QA_API_MEMORY`, `QA_E2E_CPUS`, `QA_E2E_MEMORY`

6. **`docs/deploy-runbook.md` (신규)** — 체크리스트, 단계마다 확인 명령. 비밀값은 `read -rs` 또는 숨김 프롬프트로만 받고 화면, 파일 이력, 로그에 남기지 않는다
   - 0. 사전 확인(서버): Compose v2(`docker compose version` / `docker-compose version --short`), `llm-net` 존재, compose 프로젝트 이름 `qa` 충돌 없음(`docker ps -a --filter label=com.docker.compose.project=qa`), Docker Hub pull 가능 여부(저장소 공개 여부, 서버 `docker login` 상태 [확인 필요])
   - 1. DB, 계정 생성: 이름은 가이드 Part 2 #11 — **현재 비어 있다**(예시 `gravit_qa`). runbook은 `gravit_qa`를 예시값으로 쓰고 변수로 둔다. `CREATE ROLE gravit_qa LOGIN;` → `\password gravit_qa`(클라이언트에서 해시해 보내므로 평문이 서버 로그에 남지 않음) → `CREATE DATABASE gravit_qa OWNER gravit_qa;`(PG15+에서 public 스키마 CREATE 권한 확보). psql 실행 위치(서버 컨테이너 `docker exec` / 외부 인스턴스 `docker run postgres:16-alpine psql -h`)와 Postgres 버전 [확인 필요]. 확인: QA 계정으로 `select current_user, current_database()`, 브리지 네트워크에서 `pg_isready -h <host> -p <port>`(컨테이너 기준 호스트 주소 확인)
   - 2. 서버 `.env` 값 추가: `cp -p .env .env.bak.<시각>`(600 유지) → 마지막 줄 개행 보정 → 키가 없을 때만 `read`(비밀은 `read -rs`)로 받아 `printf '%s=%s\n' >> .env`. 대상: `DOCKER_HUB_USERNAME`, `QA_API_PORT`(빈 포트 `ss -ltn` 확인), `QA_API_CPUS`, `QA_API_MEMORY`, `QA_E2E_CPUS`, `QA_E2E_MEMORY`(D2), `QA_DB_URL`, `QA_DB_USERNAME`, `QA_DB_PASSWORD`, `QA_AUTH_HOST`. 확인: 값 없이 키만 검사하는 루프(`grep -q "^$k=."`), `stat -c %a .env` = 600. **`docker compose config`(인자 없음)는 비밀값을 출력하므로 쓰지 말고 `--quiet`만** 쓴다
   - 3. GitHub 저장소 시크릿: 1절 6개. 현재 저장소 시크릿은 0개(`gh secret list` 확인), 조직 시크릿은 권한이 없어 조회 불가 → 조직 시크릿이면 이 저장소에 접근 허용, 아니면 `gh secret set NAME -R Team-Gravit/gravit-qa-server`(숨김 프롬프트). gravit-server에 등록된 값은 다시 볼 수 없으므로 원래 값을 가진 사람에게 받는다
   - 4. 도메인, HTTPS: 기존 dev 도메인(`grav-it-dev.inuappcenter.kr` → 8521)과 같은 방식으로 `QA_API_PORT` 연결. 방식 [확인 필요](리버스 프록시 종류, 인증서 발급). 확인: `curl -fsS https://{도메인}/actuator/health`
   - 5. 첫 배포: PR을 dev에 머지(또는 Actions → CD → Run workflow, dev) → `gh run watch`. 서버 확인: health 상태, 로그에서 `Successfully applied 2 migrations`, `작업 워커 준비: workerId=qa-api, types=[SETUP]`(REVIEW_COLLECT, EVIDENCE_CLEANUP 처리기 없음 경고 2줄은 정상)
   - 6. 스모크(아래 표). `BASE`는 서버에서 `http://localhost:$QA_API_PORT`, 도메인 연결 후 `https://{도메인}`. 토큰은 `read -rs TOKEN`. jq가 없으면 `docker run --rm -i ghcr.io/jqlang/jq:1.7.1`

     | # | 확인 | 기대 | 명령 요지 |
     |---|---|---|---|
     | 1 | `GET /actuator/health` | `UP` | `curl -fsS $BASE/actuator/health` |
     | 2 | 토큰 없이 `GET /api/v1/sprints` | 401 `AUTH_4011` | `curl -sS -w '\n%{http_code}\n'` |
     | 3 | 관리자 토큰으로 `GET /api/v1/sprints` | 200 `[]` | |
     | 4 | `POST /api/v1/sprints` (`notionPageUrl` = Notion `3rd Agile` 행) | 201, `sprintKey = 3rd Agile`, `status = DRAFT` | 응답에서 `SPRINT_ID` 추출 |
     | 5 | `POST /sprints/{id}/setup-jobs` → `GET /jobs/{jobId}` 30초 polling | 202 → BASELINE~APPLY 진행, `SUCCEEDED`. 기능 53개라 INTERPRET만 최소 약 11분(분당 5회) | 루프가 `status`, 단계별 상태, `lastError`를 한 줄로 출력. `SUCCEEDED/FAILED/CANCELLED`에서 멈춤. 실패 시 2→4→8분 뒤 재시도(`QUEUED`로 돌아감). 토큰 만료로 `AUTH_4012`가 나오면 다시 입력 |
     | 6 | `GET /sprints/{id}/checklist` | 기능 53개, 경고 목록. 스프린트 `status = REVIEW` | `jq '.features \| length'`, 경고 있는 기능 `screenId<TAB>코드들`, **`OPENAPI_OPERATION_MISSING` 화면 ID**: `jq -r '.features[] \| select(.warnings \| any(. == "OPENAPI_OPERATION_MISSING")) \| .feature.screenId' \| sort -u` |

     - 주의: C7 전에는 `POST /checklist/confirm`에 `shouldStartAutoRun: false`를 쓴다(점검 2-b). 이미 생겼다면 `POST /api/v1/jobs/{jobId}/cancel`
   - 7. admin-web에 알릴 값: QA 도메인 → `vercel.json` rewrite `{ "source": "/qa-api/:path*", "destination": "https://{도메인}/api/v1/:path*" }`를 SPA catch-all보다 앞에(설계 7-2)
   - 8. 되돌리기: `docker pull $U/gravit-qa-server:<sha>` → `docker tag ... :latest` → `up -d --no-deps --force-recreate qa-api`. DB 마이그레이션은 되돌리지 않는다(Flyway 기본값이 미래 버전을 무시하므로 이전 이미지도 기동). 다음 dev push가 `latest`를 다시 덮는다
   - 마무리: 확인이 끝나면 `.env.bak.*` 삭제

7. **로컬 `docs/api/openapi.yaml` 정정 (추적 안 함, 보고에 위치만)**
   - `ChecklistFeature.warnings.description` → "준비 경고 코드 문자열(`ReportWarning.code`와 같은 값). 예) `SPEC_NEEDS_CHECK`, `FIGMA_FRAME_MISSING`"
   - `ReportWarning.code.enum`에 `OPENAPI_OPERATION_MISSING`, `LLM_INPUT_TRUNCATED` 추가 (`sprint/domain/WarningCode.java`와 일치)
   - `createSprint` 응답에 `'502'`: Notion 조회 실패, 예시 `SPRINT_5021`
   - 오류 본문 예시 코드: `ErrorResponse.error`에 형식 설명(`{도메인}_{HTTP 상태}{일련번호}`)과 `examples`, 공용 응답마다 대표 코드 예시(`Unauthorized` `AUTH_4011`/`AUTH_4012`, `Forbidden` `AUTH_4031`, `AuthUnavailable` `AUTH_5031`, `BadRequest` `GLOBAL_4001`, `NotFound` `GLOBAL_4041`, `Conflict` `GLOBAL_4091`, `Unprocessable` `CHECKLIST_4221`) — `CustomErrorCode.java` 기준
   - 추가 발견: `Sprint.sprintKey` 예시 `'2026-S21'` → `'3rd Agile'`(Notion 스프린트 이름이 키)

## 결정 필요 (Decisions needed)
- [x] D1 runbook 위치 — `/docs/`가 git 추적 제외(#7)라 `docs/deploy-runbook.md`는 그대로 두면 커밋되지 않는다. **A 확정**: `.gitignore`를 `/docs/*` + `!/docs/deploy-runbook.md`로 바꿔 runbook만 추적 (B 로컬만, C 다른 경로는 기각)
- [x] D2 `QA_E2E_CPUS`, `QA_E2E_MEMORY` — **A 확정**: runbook 2단계 추가 목록에 넣는다(가이드 #12 값, C7 전 임시값 가능). compose는 바꾸지 않는다 (B compose 기본값은 기각)
- [x] D3 JVM 힙 — Java 21 기본 최대 힙은 컨테이너 메모리의 25%(1g → 256MB). **A 확정**: qa-api에 `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` (B 그대로는 기각)

## 검증
- `ci.yml`, `cd.yml`: `actionlint`(로컬에 있으면) 또는 YAML 파싱으로 문법 확인
- `docker-compose-qa.yml`: 스크래치 디렉터리에 더미 `.env`를 두고 `docker compose -f docker-compose-qa.yml config --quiet` 통과, `config qa-api`에서 healthcheck 반영 확인
- 이미지: `./gradlew bootJar` → `docker build`로 로컬 이미지를 만들고 healthcheck 명령(`curl`)이 컨테이너 안에서 실행되는지 확인(앱 기동은 DB 없이 실패하므로 명령 존재만)
- 실제 CD, 스모크는 runbook 1~4 이후 사람이 실행(범위 밖)

## Deviation Log
> implement 스킬이 구현 중 계획을 벗어난 지점을 여기에 기록한다. (작성 시점엔 비워둔다)
- `docs/api/openapi.yaml`(로컬): `BadRequest` 예시와 `ErrorResponse.message` 설명을 `[ 필드 ][ 메시지 ][ 값 ]` 문자열 배열로 정정 — 이유: 기존 문서의 "필드별 오류 객체"가 `GlobalExceptionHandler.toFieldMessage`의 실제 형식과 달랐다
- `docs/deploy-runbook.md`: 6-a에 없는 operationId를 보는 SQL(`sprint_feature.warnings`의 message) 추가 — 이유: 체크리스트 API는 경고 코드만 내려 주어 Notion `관련 API` 정정에 필요한 operationId가 보이지 않는다
- `docs/deploy-runbook.md`: 상단에 "필요한 값" 표 추가 — 이유: 사람이 미리 정해 둘 값과 넣을 위치를 한곳에서 보게 한다
- 검증: "healthcheck 명령 존재만 확인" 대신 로컬 Postgres 컨테이너 + compose로 배포 리허설(CD 스크립트와 같은 흐름) 수행 — 이유: DB가 있으면 기동, Flyway, 워커 유형, 401, 힙, 자원 한도, unhealthy 전환까지 실제로 확인할 수 있다
