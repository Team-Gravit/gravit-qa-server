# gravit-qa 배포 runbook

> 대상: dev 환경 qa-api 첫 배포와 이후 운영. qa-e2e는 C7에서 다룬다.
> 비밀값(DB 비밀번호, 토큰, 시크릿)은 숨김 입력(`read -rs`, psql `\password`, `gh secret set` 프롬프트)으로만 넣는다. 채팅, 커밋, 이슈, 로그에 남기지 않는다.
> `[확인 필요]`는 서버에서 확인한 뒤 이 문서를 고친다. 명령은 bash 기준이다.

## 순서

| 단계 | 할 일 | 어디서 |
|---|---|---|
| 0 | 사전 확인 | 서버 |
| 1 | QA DB, 계정 생성 | 서버(Postgres) |
| 2 | 서버 `.env` 값 추가 | 서버 |
| 3 | GitHub 시크릿 등록 | 로컬(`gh`) |
| 4 | 도메인, HTTPS 연결 | 서버 |
| 5 | 첫 배포 | GitHub Actions |
| 6 | 스모크 확인 | 서버 |
| 7 | admin-web에 값 전달 | - |
| 8 | 되돌리기 | 서버(필요할 때만) |

1~3을 끝낸 뒤 배포 PR을 dev에 머지하면 그 push가 첫 배포(5)다. 4는 5와 따로 진행해도 되고, 4 전에는 스모크를 서버의 `localhost`로 한다.

## 필요한 값

| 값 | 넣는 곳 | 상태 | 비고 |
|---|---|---|---|
| Postgres 호스트, 포트 | `.env` `QA_DB_URL` | 미정 (가이드 Part 2 #11) | qa-api 컨테이너에서 닿는 주소 |
| QA DB 이름, 계정 | `.env` `QA_DB_URL`, `QA_DB_USERNAME` | 미정 (#11, 이 문서 예시 `gravit_qa`) | |
| QA DB 비밀번호 | `.env` `QA_DB_PASSWORD` | 새로 정함 | **비밀**. 영숫자만 쓴다(`.env`에서 `$`, `#`, 따옴표, 공백은 다르게 해석된다) |
| `QA_API_PORT` | `.env` | 미정 (#12) | 서버의 빈 포트 |
| `QA_API_CPUS`, `QA_API_MEMORY` | `.env` | 미정 (#12) | 예: `1.0`, `1g` |
| `QA_E2E_CPUS`, `QA_E2E_MEMORY` | `.env` | 미정 (#12) | qa-e2e를 띄우지 않아도 비면 compose가 해석에 실패한다. C7 전에는 임시값 가능 |
| `QA_AUTH_HOST` | `.env` | `https://grav-it-dev.inuappcenter.kr` | admin-web이 바라보는 gravit-server |
| `DOCKER_HUB_USERNAME` | `.env`, GitHub Secret | gravit-server와 같은 계정 | 이미지 `{값}/gravit-qa-server` |
| `DOCKER_HUB_PASSWORD` | GitHub Secret | gravit-server와 같은 값 | **비밀** |
| `APPCENTER_SERVER_IP`, `_PORT`, `_USERNAME`, `_PASSWORD` | GitHub Secret | gravit-server와 같은 값 | **비밀** |
| QA 도메인 | 리버스 프록시, admin-web | 미정 (#13) | 예: `qa-api.…inuappcenter.kr` |

서버 `.env`에 이미 있는 값: `LITELLM_API_KEY`, `FIGMA_TOKEN`, `NOTION_TOKEN`, `GITHUB_TOKEN`.

---

## 0. 사전 확인 (서버)

- [ ] Compose v2

  ```bash
  docker compose version || docker-compose version --short
  ```

  기대: `Docker Compose version v2.x` 또는 `2.x.y`. v1(`1.x`)이면 `deploy.resources.limits`가 적용되지 않아 CD가 중단한다. Compose v2 설치가 먼저다.

- [ ] `llm-net`과 게이트웨이

  ```bash
  docker network inspect llm-net --format '{{range .Containers}}{{.Name}} {{end}}'
  ```

  기대: litellm 계열 컨테이너 이름. 네트워크가 없으면 CD가 만들지만, 게이트웨이가 없으면 셋업 작업의 `INTERPRET` 단계가 실패한다.

- [ ] compose 프로젝트 이름 `qa` 충돌 없음 (배포 디렉터리 이름이 프로젝트 이름이 된다)

  ```bash
  docker ps -a --filter label=com.docker.compose.project=qa --format '{{.Names}}'
  ```

  기대: 빈 출력(첫 배포 전) 또는 `gravit-qa-api`만.

- [ ] 배포 디렉터리와 `.env` 권한

  ```bash
  cd /home/serverking/gravit/qa && stat -c '%a %U' .env
  ```

  기대: `600 serverking`

- [ ] Docker Hub pull 가능 여부 `[확인 필요]`. 이미지 저장소 `{DOCKER_HUB_USERNAME}/gravit-qa-server`는 첫 push 때 만들어진다. 비공개로 만들어지면 서버가 Docker Hub에 로그인돼 있어야 한다. gravit-server 이미지를 받는 방식과 같게 맞춘다.

  ```bash
  grep -o '"https://index.docker.io/v1/"' ~/.docker/config.json   # 출력이 있으면 로그인된 상태(자격 증명은 출력하지 않는다)
  ```

## 1. QA DB, 계정 생성

이름은 가이드 Part 2 #11 값을 쓴다. 아직 비어 있어 아래는 예시값 `gravit_qa`다. 정하면 #11을 채우고 아래 이름을 바꾼다.

- [ ] Postgres 위치, 버전 `[확인 필요]`. gravit-server dev가 쓰는 인스턴스다(설계 3절: compose 외부 인스턴스). 서버의 컨테이너인지 외부 호스트인지 확인한다.

  ```bash
  docker ps --format '{{.Names}}\t{{.Image}}\t{{.Ports}}' | grep -i postgres
  ```

- [ ] 관리자 계정으로 psql 접속 (둘 중 하나). 관리자 비밀번호는 psql 프롬프트에만 입력한다(명령줄 인자나 `PGPASSWORD`로 넘기지 않는다).

  ```bash
  # 서버의 Postgres 컨테이너
  docker exec -it <postgres 컨테이너> psql -U <관리자 계정> -d postgres
  # 외부 인스턴스
  docker run --rm -it postgres:16-alpine psql -h <호스트> -p <포트> -U <관리자 계정> -d postgres
  ```

- [ ] 계정, DB 생성 (psql 안에서)

  ```sql
  SELECT version();
  CREATE ROLE gravit_qa LOGIN;
  \password gravit_qa
  CREATE DATABASE gravit_qa OWNER gravit_qa;
  ```

  - `\password`는 비밀번호를 숨김 입력받아 클라이언트에서 해시한 뒤 보낸다. 평문이 서버 로그와 psql 기록에 남지 않는다. `CREATE ROLE ... PASSWORD '...'`로 쓰지 않는다.
  - DB 소유자를 QA 계정으로 둔다. PG15부터 public 스키마의 CREATE 권한이 DB 소유자에게만 있어, Flyway가 테이블을 만들려면 필요하다.
  - 테스트(Testcontainers)는 Postgres 16으로 V1, V2를 검증했다. 서버 버전이 많이 낮으면 알려 둔다.

- [ ] 확인: QA 계정으로 접속 (위와 같은 방식으로 `-U gravit_qa -d gravit_qa`)

  ```bash
  docker run --rm -it postgres:16-alpine psql -h <호스트> -p <포트> -U gravit_qa -d gravit_qa -c 'select current_user, current_database();'
  ```

  기대: `gravit_qa | gravit_qa`

- [ ] 확인: 컨테이너에서 닿는 주소인지 (`QA_DB_URL`에 넣을 호스트로)

  ```bash
  docker run --rm postgres:16-alpine pg_isready -h <호스트> -p <포트>
  ```

  기대: `accepting connections`. qa-api는 브리지 네트워크의 컨테이너라 `localhost`는 컨테이너 자신이다. Postgres가 다른 compose 네트워크의 컨테이너면 컨테이너 이름으로는 닿지 않으니 호스트에 공개된 포트(호스트 IP)로 접속한다.

## 2. 서버 `.env` 값 추가

이미 있는 키는 건드리지 않고 없는 키만 덧붙인다. CD는 `.env`를 읽지도 쓰지도 않는다.

- [ ] 백업, 개행 보정, 입력 함수

  ```bash
  cd /home/serverking/gravit/qa
  cp -p .env ".env.bak.$(date +%Y%m%d%H%M%S)"   # 권한 600 유지. 마무리에서 지운다
  [ -z "$(tail -c1 .env)" ] || echo >> .env      # 마지막 줄에 개행이 없으면 추가

  add_env() {   # $1 = 키, $2 = secret 이면 숨김 입력
    if grep -q "^$1=" .env; then echo "$1: 이미 있음, 건너뜀"; return; fi
    if [ "${2:-}" = secret ]; then read -rs -p "$1: " v; echo; else read -r -p "$1: " v; fi
    printf '%s=%s\n' "$1" "$v" >> .env
    unset v
  }
  ```

- [ ] 빈 포트 고르기

  ```bash
  ss -ltnH "sport = :<포트>"   # 출력이 없으면 빈 포트
  ```

- [ ] 값 입력

  ```bash
  add_env DOCKER_HUB_USERNAME
  add_env QA_API_PORT
  add_env QA_API_CPUS
  add_env QA_API_MEMORY
  add_env QA_E2E_CPUS
  add_env QA_E2E_MEMORY
  add_env QA_DB_URL          # jdbc:postgresql://<호스트>:<포트>/gravit_qa
  add_env QA_DB_USERNAME
  add_env QA_DB_PASSWORD secret
  add_env QA_AUTH_HOST       # https://grav-it-dev.inuappcenter.kr
  ```

- [ ] 확인: 값은 출력하지 않고 키만 검사한다

  ```bash
  for k in DOCKER_HUB_USERNAME QA_API_PORT QA_API_CPUS QA_API_MEMORY QA_E2E_CPUS QA_E2E_MEMORY \
           QA_DB_URL QA_DB_USERNAME QA_DB_PASSWORD QA_AUTH_HOST \
           FIGMA_TOKEN NOTION_TOKEN GITHUB_TOKEN LITELLM_API_KEY; do
    grep -q "^$k=." .env && echo "ok      $k" || echo "MISSING $k"
  done
  stat -c '%a' .env   # 600
  ```

  기대: 모두 `ok`, 권한 `600`.

> `cat .env`, 인자 없는 `docker compose config`는 비밀값을 화면에 출력한다. compose 검증은 `docker compose -f docker-compose-qa.yml config --quiet`만 쓴다(compose 파일은 첫 CD가 전송한다).

## 3. GitHub 저장소 시크릿 (로컬)

- [ ] 현재 상태

  ```bash
  gh secret list -R Team-Gravit/gravit-qa-server
  ```

  2026-10-17 기준 저장소 시크릿은 0개다. 조직 시크릿은 조직 관리자만 조회할 수 있다. gravit-server가 조직 시크릿을 쓴다면 조직 설정에서 이 저장소에 접근을 허용하는 것으로 끝난다 `[확인 필요: 조직 관리자]`.

- [ ] 저장소 시크릿으로 등록할 때 (프롬프트가 입력을 숨긴다)

  ```bash
  for s in DOCKER_HUB_USERNAME DOCKER_HUB_PASSWORD \
           APPCENTER_SERVER_IP APPCENTER_SERVER_PORT APPCENTER_SERVER_USERNAME APPCENTER_SERVER_PASSWORD; do
    gh secret set "$s" -R Team-Gravit/gravit-qa-server
  done
  ```

  값은 gravit-server에 등록한 것과 같다. GitHub에 등록된 값은 다시 볼 수 없으니 원래 값을 가진 사람에게 받는다.

- [ ] 확인: `gh secret list -R Team-Gravit/gravit-qa-server`에 6개 이름(또는 조직 시크릿 접근 허용)

## 4. 도메인, HTTPS `[확인 필요]`

기존 dev 도메인 `grav-it-dev.inuappcenter.kr`이 호스트 포트 8521(`gravit-server-dev`)로 연결된 방식과 같게, QA 도메인을 `QA_API_PORT`로 연결한다.

- [ ] 연결 방식 확인 (예)

  ```bash
  docker ps --format '{{.Names}}\t{{.Image}}' | grep -iE 'nginx|caddy|traefik|proxy'
  grep -rl 8521 /etc/nginx 2>/dev/null
  ```

- [ ] 도메인과 인증서 방식을 가이드 Part 2 #13에 적는다
- [ ] 확인 (5단계 이후)

  ```bash
  curl -fsS https://<QA 도메인>/actuator/health   # {"status":"UP"}
  ```

> compose는 `QA_API_PORT`를 모든 인터페이스에 공개한다(gravit-server와 같음). 포트로 직접 들어오는 접근을 막으려면 방화벽이나 `127.0.0.1:` 바인딩을 따로 정한다.

## 5. 첫 배포

- [ ] 1~3을 끝낸 뒤 배포 PR을 dev에 머지한다. 이미 머지돼 있으면 수동으로 실행한다.

  ```bash
  gh workflow run cd.yml -R Team-Gravit/gravit-qa-server --ref dev
  gh run watch -R Team-Gravit/gravit-qa-server
  ```

  CD 흐름: CI(테스트) → 이미지 빌드, push(`latest`, 커밋 SHA) → `docker-compose-qa.yml` 전송 → qa-api pull, 재생성 → healthy 대기(최대 180초).

- [ ] 서버 확인

  ```bash
  docker inspect -f '{{.State.Health.Status}}' gravit-qa-api   # healthy
  docker logs gravit-qa-api 2>&1 | grep -E 'Successfully applied|작업 워커 준비|처리기가 없어'
  ```

  기대:
  - `Successfully applied 2 migrations to schema "public", now at version v2` (첫 기동에만)
  - `작업 워커 준비: workerId=qa-api, types=[SETUP]`
  - `REVIEW_COLLECT 작업 처리기가 없어 소비 대상에서 제외합니다.`, `EVIDENCE_CLEANUP ...` 경고 2줄은 정상이다(처리기는 C7, C8)

- CD가 healthy 대기에서 실패하면 서버에서 `docker logs --tail 200 gravit-qa-api`로 원인을 본다(저장소가 public이라 Actions 로그에는 남기지 않는다). 흔한 원인: `QA_DB_URL` 호스트, 필수 변수 누락(`QA_AUTH_HOST` 등), 메모리 한도.

## 6. 스모크 확인

- [ ] 준비

  ```bash
  cd /home/serverking/gravit/qa
  BASE="http://localhost:$(grep '^QA_API_PORT=' .env | cut -d= -f2)"   # 도메인 연결 후: BASE=https://<QA 도메인>
  mkdir -p ~/qa-smoke && cd ~/qa-smoke
  read -rs -p '관리자 accessToken: ' TOKEN; echo
  api() { curl -sS -H "Authorization: Bearer $TOKEN" "$@"; }
  command -v jq >/dev/null || jq() { docker run --rm -i ghcr.io/jqlang/jq:1.7.1 "$@"; }   # jq는 표준 입력으로만 쓴다
  ```

  토큰은 `QA_AUTH_HOST`와 같은 환경(dev)의 관리자 토큰이다. admin-web(dev)에 관리자로 로그인한 브라우저의 개발자 도구에서 복사한다(저장 위치 `[확인 필요]`).

| # | 확인 | 기대 |
|---|---|---|
| 1 | `GET /actuator/health` | `UP` |
| 2 | 토큰 없이 `GET /api/v1/sprints` | 401 `AUTH_4011` |
| 3 | 관리자 토큰으로 `GET /api/v1/sprints` | 200 `[]` |
| 4 | `POST /api/v1/sprints` (Notion `3rd Agile` 행 URL) | 201, `sprintKey = 3rd Agile`, `status = DRAFT` |
| 5 | `POST /api/v1/sprints/{id}/setup-jobs` → `GET /api/v1/jobs/{jobId}` polling | 202 → BASELINE~APPLY 진행 → `SUCCEEDED`. 기능 53개라 `INTERPRET`만 최소 약 11분(분당 5회 상한) |
| 6 | 끝난 뒤 `GET /api/v1/sprints/{id}/checklist` | 기능 53개, 경고 목록. 스프린트 `status = REVIEW` |

- [ ] 1, 2, 3

  ```bash
  curl -sS "$BASE/actuator/health"; echo                       # {"status":"UP"}
  curl -sS -w '\n%{http_code}\n' "$BASE/api/v1/sprints"        # {"error":"AUTH_4011",...} / 401
  api -w '\n%{http_code}\n' "$BASE/api/v1/sprints"             # [] / 200
  ```

- [ ] 4. 스프린트 등록

  ```bash
  read -r -p 'Notion 3rd Agile 행 URL: ' NOTION_URL
  api -X POST -H 'Content-Type: application/json' -d "{\"notionPageUrl\":\"$NOTION_URL\"}" \
    -o sprint.json -w '%{http_code}\n' "$BASE/api/v1/sprints"   # 201
  jq '{id, sprintKey, status}' < sprint.json                    # "3rd Agile", "DRAFT"
  SPRINT_ID=$(jq -r .id < sprint.json); echo "SPRINT_ID=$SPRINT_ID"
  ```

  실패 코드: `SPRINT_4001`(스프린트 DB 행이 아님), `SPRINT_4042`(Integration 미연결), `SPRINT_4091`(이미 등록), `SPRINT_5021`(Notion 일시 실패).

- [ ] 5. QA 준비하기 실행, 진행 확인 (30초 간격)

  ```bash
  JOB_ID=$(api -X POST "$BASE/api/v1/sprints/$SPRINT_ID/setup-jobs" | jq -r .id); echo "JOB_ID=$JOB_ID"
  while :; do
    api "$BASE/api/v1/jobs/$JOB_ID" > job.json
    if jq -e .error < job.json >/dev/null; then cat job.json; echo; break; fi
    jq -r '"\(.status) \(.attempt)/\(.maxAttempts) " + ([.steps[] | "\(.name):\(.status)"] | join(" ")) + (if .lastError then " lastError=\(.lastError)" else "" end)' < job.json
    case "$(jq -r .status < job.json)" in SUCCEEDED|FAILED|CANCELLED) break ;; esac
    sleep 30
  done
  ```

  - 단계: `BASELINE → NOTION → FIGMA → DIFF → OPENAPI → INTERPRET → VALIDATE → APPLY`
  - 실패하면 2→4→8분 뒤 재시도한다(`QUEUED`로 돌아가고 시도 횟수 증가). 4번째 시도까지 실패하면 `FAILED`, 스프린트는 `PREPARE_FAILED`
  - 루프가 오류 본문을 출력하고 멈추면: `AUTH_4012`(토큰 만료)는 `read -rs`로 `TOKEN`을 다시 받고 `while`부터 다시 돌린다. 작업은 서버에서 계속 진행 중이다

- [ ] 6. 체크리스트와 경고

  ```bash
  api "$BASE/api/v1/sprints/$SPRINT_ID" | jq '{status, latestSetupJobId}'   # "REVIEW"
  api "$BASE/api/v1/sprints/$SPRINT_ID/checklist" > checklist.json
  jq '.features | length' < checklist.json                                  # 53
  jq -r '.features[] | select(.warnings | length > 0) | "\(.feature.screenId)\t\(.warnings | join(","))"' < checklist.json
  ```

- [ ] 6-a. Notion `관련 API` 정정 대상: `OPENAPI_OPERATION_MISSING`이 붙은 화면 ID

  ```bash
  jq -r '.features[] | select(.warnings | any(. == "OPENAPI_OPERATION_MISSING")) | .feature.screenId' < checklist.json | sort -u
  ```

  API는 경고 코드만 내려 준다. 없는 operationId까지 보려면 1단계와 같은 방식으로 QA 계정 psql에 접속해 조회한다.

  ```sql
  SELECT f.screen_id, w ->> 'message' AS detail
  FROM sprint_feature sf
           JOIN feature f ON f.id = sf.feature_id
           CROSS JOIN LATERAL jsonb_array_elements(sf.warnings) w
  WHERE sf.sprint_id = <SPRINT_ID>
    AND w ->> 'code' = 'OPENAPI_OPERATION_MISSING'
  ORDER BY f.screen_id;
  ```

> **C7 전 주의**: 분류 확정(`POST /api/v1/sprints/{id}/checklist/confirm`)은 `shouldStartAutoRun: false`로 보낸다. `AUTO_RUN` 작업은 처리기가 없어 `QUEUED`로 남고, 남아 있는 동안 스프린트 종료가 `SPRINT_4094`로 막힌다. 이미 생겼다면 `POST /api/v1/jobs/{jobId}/cancel`로 취소한다.

## 7. admin-web에 알릴 값

- [ ] QA 도메인
- [ ] `vercel.json` rewrite. SPA catch-all보다 앞에 둔다(설계 7-2, 현행 `/ravvit-api`와 같은 패턴)

  ```json
  { "source": "/qa-api/:path*", "destination": "https://<QA 도메인>/api/v1/:path*" }
  ```

## 8. 되돌리기 (필요할 때만)

CD는 `latest`와 커밋 SHA 태그를 함께 push한다. compose는 `latest`를 쓰므로, 이전 SHA 이미지를 `latest`로 다시 태그해 qa-api만 재생성한다.

```bash
cd /home/serverking/gravit/qa
U=$(grep '^DOCKER_HUB_USERNAME=' .env | cut -d= -f2)
SHA=<되돌릴 dev 커밋 SHA 40자>
docker pull "$U/gravit-qa-server:$SHA"
docker tag "$U/gravit-qa-server:$SHA" "$U/gravit-qa-server:latest"
docker compose -f docker-compose-qa.yml up -d --no-deps --force-recreate qa-api   # pull 하지 않는다
docker inspect -f '{{.State.Health.Status}}' gravit-qa-api
```

- DB 마이그레이션은 되돌리지 않는다. 이전 이미지는 DB에 자기보다 새 마이그레이션이 있어도 기동한다(Flyway 기본값이 미래 버전을 무시). 단, 새 마이그레이션이 이전 코드가 쓰는 컬럼을 지우거나 바꿨다면 이전 이미지가 실패할 수 있으니 되돌리기 전에 확인한다.
- 다음 dev push가 `latest`를 다시 덮어쓴다. 되돌린 상태를 유지하려면 dev에서 원인 커밋을 revert한다.

## 마무리

- [ ] 확인이 끝나면 `.env` 백업을 지운다: `rm /home/serverking/gravit/qa/.env.bak.*`
- [ ] 가이드 Part 2 #11~#14 빈칸을 채운다(DB, 포트, 자원 한도, 도메인, 이미지 이름 `{DOCKER_HUB_USERNAME}/gravit-qa-server`)
