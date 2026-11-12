# [PLAN-13] zsh 서버에서 배포 스크립트 실패 수정

> 이슈: #13
> 브랜치: cicd/13-zsh-deploy-script

## 목표
앱센터 서버 `serverking` 계정의 로그인 셸이 zsh라 `appleboy/ssh-action`의 원격 스크립트가 zsh로 실행되고, `$COMPOSE -f ...`가 `command not found: docker compose`로 실패한다. 원격 스크립트를 bash와 zsh에서 같은 동작이 되게 고치고, 기존 동작은 바꾸지 않는다.
함께 `docs/deploy-runbook.md` "C7 전 주의"의 요청 필드명 오기(`shouldStartAutoRun` → `startAutoRun`)를 바로잡고, 같은 zsh 원인으로 깨지는 runbook 명령(D1, D2)을 두 셸 공통으로 고친다.

## 점검 결과 (코드, 로컬 확인)

| # | 항목 | 결과 | 근거 |
|---|---|---|---|
| 1 | 실패 지점 | `COMPOSE="docker compose"` 뒤 `$COMPOSE -f ...` 2줄. zsh는 따옴표 없는 변수를 단어 분리하지 않아(`SH_WORD_SPLIT` 기본 꺼짐) `docker compose`라는 이름의 명령을 찾는다 | `cd.yml` qa-api 갱신 step, 서버 재현 `zsh -c 'C="docker compose"; $C version'` |
| 2 | 나머지 줄의 zsh 차이 | 없음(코드 읽기 기준). `set -eu`, `[ ! -f ]`, `if ... >/dev/null 2>&1`, `grep -q`, `\|\|`, `until [ "$(...)" = "healthy" ]`(단일 `=`), `$((i + 1))`, `sleep`, `echo`(백슬래시 없음) 모두 두 셸에서 같다. 원격 스크립트는 `-c`로 실행되는 비대화형 셸이라 `#` 주석도 정상 처리된다. 검증 3에서 스크립트 전체를 두 셸로 돌려 확인한다 | 로컬 `zsh -c '[ "healthy" = "healthy" ] && echo eq-ok'`, `zsh -f -c 'echo a # b'` → `a` |
| 3 | 요청 필드명 | `ChecklistConfirmRequest`의 JSON 필드는 `startAutoRun`(`Boolean`, null이면 true). `shouldStartAutoRun()`은 이 값을 해석하는 메서드라 요청 본문에 쓰면 무시되고 기본값 true로 `AUTO_RUN`이 생긴다 | `checklist/dto/request/ChecklistConfirmRequest.java:11, 21` |
| 4 | **발견: runbook의 `read -p`도 zsh에서 실패** | runbook 2단계 `add_env`(144행, 2곳), 6단계 준비(262행), 4번 스프린트 등록(289행)이 `read -r(s) -p '프롬프트' 변수`를 쓴다. zsh의 `-p`는 프롬프트가 아니라 "코프로세스에서 읽기"라 `read: -p: no coprocess`로 실패한다. `add_env`에서는 `read`가 실패한 뒤 `printf '%s=%s\n' "$1" "$v"`가 이어 실행돼 **`.env`에 빈 값(`KEY=`)이 기록**되고, 다시 돌리면 "이미 있음, 건너뜀"이 된다 → 결정 D1 | 로컬 `echo test \| zsh -c 'read -r -p "x: " v'` → `no coprocess`, rc=1. 사용자 서버 작업에서 같은 현상 확인 |
| 5 | **발견: 여러 줄 붙여 넣기와 `read`** | `read`가 들어 있는 줄을 다른 줄과 함께 붙여 넣으면 `read`가 다음 줄(다음 명령)을 값으로 읽는다. 2단계 `add_env` 10줄 블록, 6단계 준비 블록(262행 뒤에 `api()`, `jq()` 정의), 4번 블록(289행 뒤에 `api -X POST ...`)이 해당한다 → 결정 D1 | 사용자 서버 작업에서 2단계 발생 확인 |
| 6 | **발견: zsh 대화형 셸의 `#` 주석** | zsh 기본값은 대화형 셸에서 `#`를 주석으로 보지 않는다(`interactivecomments` 꺼짐). 꺼져 있으면 runbook 코드 블록의 주석 22줄(줄 끝 20, 주석 전용 2)이 깨진다. 예: 139행 `cp`가 인자 과다로 실패, 140행 `echo >> .env`가 주석 문장을 `.env`에 기록, 260행 `BASE=...`가 설정되지 않음, 96, 98행은 `command not found: #` → 결정 D2 | 로컬 `printf 'echo a # b\n' \| zsh -f -i` → `a # b`, `setopt interactivecomments` 뒤 → `a` |

## 확정한 선택 (브리프가 계획서에 맡긴 항목)

- **Compose 명령을 셸 함수 `compose()`로 감싼다.** v2 확인 분기에서 `COMPOSE=` 대입 대신 `compose() { docker compose "$@"; }` 또는 `compose() { docker-compose "$@"; }`를 정의하고, 호출부는 `compose -f docker-compose-qa.yml ...`로 바꾼다.
  - 고른 이유: (1) 함수와 `"$@"`는 POSIX 문법이라 bash, zsh, sh에서 인자가 똑같이 전달된다. (2) 바뀌는 줄이 분기 2줄, 호출 2줄뿐이라 나머지 동작이 그대로다. (3) 스크립트를 실행하는 셸이 무엇이든 의존하지 않는다.
  - 스크립트 전체를 bash로 실행(`bash -s <<'EOF' ... EOF`)을 고르지 않은 이유: (1) 원격 스크립트가 표준 입력을 heredoc으로 쓰게 되어, 안의 명령 중 하나라도 표준 입력을 읽으면 남은 스크립트를 삼키는 함정이 생긴다. (2) `bash -c '...'`는 스크립트 안의 작은따옴표(`'{{.State.Health.Status}}'`, `'^2'`)와 충돌해 따옴표를 한 겹 더 다뤄야 한다. (3) `set -eu`, 종료 코드 전달을 바깥 셸과 안쪽 bash 양쪽에서 신경 써야 한다.
  - 배열(`COMPOSE=(docker compose)` + `"${COMPOSE[@]}"`)은 bash, zsh에서 동작하지만 POSIX sh에 없고, zsh 전용 `${=COMPOSE}`는 bash에서 동작하지 않아 고르지 않는다.

## 영향 범위
### 신규 파일
- 없음

### 수정 파일
- `.github/workflows/cd.yml` — qa-api 갱신 step 원격 스크립트의 Compose 분기와 호출 2줄을 함수 방식으로 변경, 이유 주석 1줄 추가
- `docs/deploy-runbook.md` — `startAutoRun` 정정, 5행 셸 기준 문장, `read -p` 4곳 두 셸 공통 형태(D1), `add_env` 빈 값 차단, 2단계 입력 방식 변경, `read` 줄 분리, 코드 블록 안 `#` 주석 22줄을 본문으로 이동(D2)

## 구현 계획
> Java 코드, Flyway 변경 없음. Entity, Repository, Service, Facade, DTO, Controller 해당 없음(Facade 불필요).

1. **`.github/workflows/cd.yml` (수정, `qa-api 갱신` step의 `script`만)**
   - 변경 전
     ```sh
     # deploy.resources.limits는 Compose v2에서만 적용된다.
     if docker compose version >/dev/null 2>&1; then
       COMPOSE="docker compose"
     elif docker-compose version --short 2>/dev/null | grep -q '^2'; then
       COMPOSE="docker-compose"
     else
     ...
     $COMPOSE -f docker-compose-qa.yml pull qa-api
     $COMPOSE -f docker-compose-qa.yml up -d --no-deps --force-recreate qa-api
     ```
   - 변경 후
     ```sh
     # deploy.resources.limits는 Compose v2에서만 적용된다.
     # 서버 로그인 셸(zsh)은 따옴표 없는 변수를 단어 분리하지 않아, 명령을 변수 대신 함수로 고른다.
     if docker compose version >/dev/null 2>&1; then
       compose() { docker compose "$@"; }
     elif docker-compose version --short 2>/dev/null | grep -q '^2'; then
       compose() { docker-compose "$@"; }
     else
     ...
     compose -f docker-compose-qa.yml pull qa-api
     compose -f docker-compose-qa.yml up -d --no-deps --force-recreate qa-api
     ```
   - 그 밖의 줄(`.env` 확인, `else` 분기 메시지와 `exit 1`, `llm-net` 생성, qa-e2e 주석, healthy 대기 36회 x 5초, 로그 미출력 주석, `docker image prune -f`)과 다른 job, step은 손대지 않는다. 원격 스크립트의 `#` 주석은 비대화형 셸이라 그대로 둔다(점검 2)

2. **`docs/deploy-runbook.md` (수정)**
   > 원칙: 코드 블록 안에는 명령만 둔다. 설명은 블록 바로 위나 아래 본문, 또는 "기대:" 줄에 둔다. psql 안에서 실행하는 SQL 블록(`-- 주석`)은 대상이 아니다.

   - **5행**: `명령은 bash 기준이다.` → `` 명령은 bash와 zsh(서버 로그인 셸)에서 같게 동작하도록 쓴다. zsh에서 뜻이 다른 `read -p`와, 설정(`interactivecomments`)에 따라 명령의 인자가 되는 코드 블록 안 `#` 주석은 쓰지 않는다. ``
   - **0단계 Docker Hub (80행)**: 줄 끝 `# 출력이 있으면 로그인된 상태(자격 증명은 출력하지 않는다)` 제거 → 블록 아래 `기대: 출력이 있으면 로그인된 상태다. 자격 증명은 출력하지 않는다.`
   - **1단계 psql 접속 (96~99행)**: 주석 전용 줄 2개를 없애고 블록을 둘로 나눈다. 본문 `서버의 Postgres 컨테이너:` + `docker exec ...` 블록, 본문 `외부 인스턴스:` + `docker run ...` 블록
   - **2단계 "백업, 개행 보정, 입력 함수" (138~147행)**
     ```bash
     cd /home/serverking/gravit/qa
     cp -p .env ".env.bak.$(date +%Y%m%d%H%M%S)"
     [ -z "$(tail -c1 .env)" ] || echo >> .env

     add_env() {
       if grep -q "^$1=" .env; then echo "$1: 이미 있음, 건너뜀"; return; fi
       printf '%s: ' "$1"
       if [ "${2:-}" = secret ]; then read -rs v; echo; else read -r v; fi
       [ -n "$v" ] || { echo "$1: 빈 값, 건너뜀"; return; }
       printf '%s=%s\n' "$1" "$v" >> .env
       unset v
     }
     ```
     - 블록 아래 본문 3줄: 백업은 `cp -p`라 권한 600이 유지되고 마무리에서 지운다 / 셋째 줄은 마지막 줄에 개행이 없으면 추가한다 / `add_env 키 [secret]`은 `secret`이면 숨김 입력으로 받고, 이미 있는 키와 빈 값(입력 실패 포함)은 쓰지 않는다
     - 빈 값 차단 줄은 사용자 지정 문구 그대로 (D1)
   - **2단계 "빈 포트 고르기" (153행)**: 줄 끝 주석 제거 → 블록 아래 `기대: 출력이 없으면 빈 포트다.`
   - **2단계 "값 입력" (158~169행)**: 둘로 나눈다 (D1)
     - `- [ ] 비밀이 아닌 값` — 본문: `<...>`를 채운 뒤 블록째 붙여 넣는다. 대화형 입력이 없어 한 번에 붙여 넣어도 된다. 이미 있는 키와 채우지 않은 항목은 건너뛴다
       ```bash
       for kv in \
         'DOCKER_HUB_USERNAME=<값>' \
         'QA_API_PORT=<포트>' \
         'QA_API_CPUS=<값>' \
         'QA_API_MEMORY=<값>' \
         'QA_E2E_CPUS=<값>' \
         'QA_E2E_MEMORY=<값>' \
         'QA_DB_URL=jdbc:postgresql://<호스트>:<포트>/gravit_qa' \
         'QA_DB_USERNAME=gravit_qa' \
         'QA_AUTH_HOST=https://grav-it-dev.inuappcenter.kr'; do
         k=${kv%%=*}
         if grep -q "^$k=" .env; then echo "$k: 이미 있음, 건너뜀"; continue; fi
         case "$kv" in *= | *'<'*) echo "$k: 값을 채우지 않음, 건너뜀"; continue ;; esac
         printf '%s\n' "$kv" >> .env
       done
       ```
       - 2단계 머리말 "이미 있는 키는 건드리지 않는다"를 지키려고 `printf`를 키 검사와 함께 루프로 돈다. `<...>`가 남았거나 값이 비면 쓰지 않는다(빈 값 차단과 같은 취지)
       - 기존 줄 끝 주석의 예시값(`jdbc:postgresql://...`, `https://grav-it-dev...`)은 항목 값 자리로 옮겨졌다
     - `- [ ] 비밀 값` — 본문: `이 줄만 따로 붙여 넣는다. 여러 줄을 함께 붙여 넣으면 read가 다음 줄을 값으로 읽는다.`(`read`는 백틱) + 블록 `add_env QA_DB_PASSWORD secret`
   - **2단계 확인 (179행)**: `stat -c '%a' .env   # 600` → `stat -c '%a' .env`. 블록 아래 `기대: 모두 ok, 권한 600.`은 이미 있다
   - **4단계 확인 (224행)**: 줄 끝 주석 제거 → 블록 아래 `` 기대: `{"status":"UP"}` ``
   - **5단계 서버 확인 (243행)**: 줄 끝 `# healthy` 제거 → 기존 "기대:" 목록 맨 앞에 `` - `healthy` `` 추가
   - **6단계 준비 (259~264행)**
     - 블록에서 `read` 줄을 빼고 줄 끝 주석 2개(260, 264행)를 제거한다. 남는 블록: `cd`, `BASE=...`, `mkdir ... && cd ...`, `api() {...}`, `command -v jq ... || jq() {...}`
     - 블록 아래 본문: `도메인 연결 후에는 BASE=https://<QA 도메인>으로 바꾼다.` / `jq가 없으면 컨테이너로 대신한다. 이때 jq는 표준 입력으로만 쓴다.` (명령, 값은 백틱)
     - 이어서 본문 `토큰: 이 줄만 따로 붙여 넣는다.` + 블록 `printf '관리자 accessToken: '; read -rs TOKEN; echo`. `api()`는 호출할 때 `$TOKEN`을 읽으므로 정의 뒤에 받아도 동작이 같다
     - 기존 토큰 설명 문단(267행)은 그 아래 그대로
   - **6단계 1, 2, 3 (281~283행)**: 줄 끝 주석 3개 제거 → 블록 아래 `` 기대: 차례로 `{"status":"UP"}`, `{"error":"AUTH_4011",...}`와 `401`, `[]`와 `200`. ``
   - **6단계 4. 스프린트 등록 (289~293행)**
     - 본문 `Notion 행 URL: 이 줄만 따로 붙여 넣는다.` + 블록 `printf 'Notion 3rd Agile 행 URL: '; read -r NOTION_URL`
     - 이어서 나머지 블록(`api -X POST ...`, `jq ...`, `SPRINT_ID=...`)에서 줄 끝 주석 2개(291, 292행) 제거 → 블록 아래 `` 기대: `201`, `sprintKey`가 `"3rd Agile"`, `status`가 `"DRAFT"`. ``
   - **6단계 5 (313행)**: `` `read -rs`로 `TOKEN`을 다시 받고 `` → `` 준비의 `TOKEN` 입력 줄을 다시 붙여 넣고 ``
   - **6단계 6 (318, 320행)**: 줄 끝 주석 2개 제거 → 블록 아래 `` 기대: 스프린트 `status`가 `"REVIEW"`, 기능 수 `53`, 이어서 경고가 있는 기능마다 `화면ID<탭>경고 코드들`. ``
   - **C7 전 주의 (342행)**: `` `shouldStartAutoRun: false`로 보낸다 `` → `` `startAutoRun: false`로 보낸다 ``. 나머지 문장은 그대로
   - **8단계 되돌리기 (363행)**: 줄 끝 `# pull 하지 않는다` 제거 → 블록 아래 목록 맨 앞에 `` - `up`만 하고 pull 하지 않는다. pull하면 Docker Hub의 `latest`가 다시 내려와 되돌린 태그를 덮는다. ``
   - 위 22줄 외의 문장, 표, SQL 블록, JSON 블록은 손대지 않는다

## 결정 필요 (Decisions needed)
- [x] D1 runbook의 `read -p` 4곳(점검 4, 5) — **B 확정**: `printf '프롬프트'; read -r(s) v`로 바꿔 두 셸에서 그대로 동작하게 한다. 사용자 추가 지시 2건 함께 반영: (1) `add_env`에서 `read` 실패나 빈 입력이면 `.env`에 쓰지 않는다(`[ -n "$v" ] || { echo "$1: 빈 값, 건너뜀"; return; }`를 `printf` 앞에), (2) 2단계는 비밀이 아닌 값을 한 번에 추가하는 블록으로 두고 대화형 `read`는 `QA_DB_PASSWORD` 한 줄만, 그 위에 "이 줄만 따로 붙여 넣는다". 6단계의 `read` 2곳도 같은 원인이라 별도 블록으로 분리한다. 서버 `.env` 2단계는 이미 끝났고(14개 키, 권한 600) 이번 수정은 재설치, 다른 사람을 위한 것이다 (A 안내 한 줄, C 범위 밖은 기각)
- [x] D2 zsh 대화형 셸의 `#` 주석(점검 6) — **B 확정**: `interactivecomments` 설정에 의존하지 않게 고친다. 코드 블록 안 줄 끝 주석과 주석 전용 줄을 모두 본문("기대:" 줄 포함)으로 옮긴다. psql 안에서 실행하는 SQL 블록의 `--` 주석은 그대로 둔다. 서버 설정값과 무관하게 이 방식으로 간다 (A 전제 명시, C 범위 밖은 기각)

## 검증
> 로컬 zsh 5.9, bash 3.2.57, dash. actionlint, PyYAML은 로컬에 없다.
1. **추출**: `cd.yml`에서 `qa-api 갱신` step의 `script` 값을 `ruby -ryaml`(macOS 기본) 또는 들여쓰기 기준 awk로 스크래치 파일에 꺼낸다. 같은 방식으로 변경 전 스크립트(`git show origin/dev:.github/workflows/cd.yml`)도 꺼낸다. 이 파싱이 성공하는 것으로 YAML 문법도 확인한다
2. **문법**: 변경 후 스크립트를 `zsh -n`, `bash -n`, `dash -n`으로 확인
3. **실행 비교 (docker 대신 기록용 대역)**: 스크래치에 `bin/docker`, `bin/docker-compose`, `bin/sleep` 대역을 두고 `PATH` 앞에 붙인다. 대역은 받은 인자를 `[인자]` 형태로 하나씩 감싸 로그에 남긴다(단어 분리 여부가 드러나게). 스크립트의 `cd /home/serverking/gravit/qa`만 스크래치 작업 디렉터리로 바꾸고 `.env`를 둔다. 아래 시나리오를 zsh와 bash로 각각 돌려 종료 코드, 출력, 호출 로그를 `diff`로 비교한다
   - a. `docker compose` 있음 → `[compose][-f][docker-compose-qa.yml][pull][qa-api]`, `[compose][-f][docker-compose-qa.yml][up][-d][--no-deps][--force-recreate][qa-api]` 호출, `qa-api healthy`, 종료 0
   - b. `docker compose` 없음, `docker-compose` 2.1.0 → `docker-compose`로 같은 인자 호출, 종료 0
   - c. 둘 다 v2 아님(`docker-compose` 1.29.2) → `Compose v2가 필요합니다.`, 종료 1, pull/up 호출 없음
   - d. `.env` 없음 → 안내 메시지, 종료 1
   - e. healthy가 되지 않음(대역 `sleep`으로 즉시 진행) → 36회 확인 뒤 180초 안내 메시지, 종료 1
4. **회귀 확인**: 변경 전 스크립트를 시나리오 a로 돌려 zsh는 `command not found: docker compose`로 실패, bash는 통과하는 것을 보여 3의 검증이 버그를 잡아낸다는 것을 확인한다
5. **runbook 정적 확인**
   - `grep -n 'shouldStartAutoRun'` 결과 없음, `startAutoRun: false` 1건, `read -[a-z]*p` 결과 없음
   - runbook의 `bash` 코드 블록 내용만 추출해 `grep -nE '\s#\s|^\s*#'`로 남은 주석 0건. shebang이나 문자열 안의 `#`(grep 패턴 등)가 걸리면 예외로 보고에 적는다
6. **runbook 2단계, 6단계 블록 실행 (zsh, bash 각각)**: 스크래치 디렉터리의 가짜 `.env`(개행 없는 마지막 줄, 기존 키 1개 포함)에 대해 runbook에서 꺼낸 블록을 그대로 실행한다
   - 백업, 개행 보정 블록: 백업 파일 생성, 마지막 줄 개행 추가
   - `add_env`: 값 입력 → `KEY=값` 기록, 빈 입력 → "빈 값, 건너뜀" 후 기록 없음, 입력 없음(EOF, `read` 실패) → 기록 없음, 이미 있는 키 → 건너뜀
   - 비밀이 아닌 값 루프: 채운 값만 기록, 이미 있는 키와 `<...>`가 남은 항목은 건너뜀. 두 셸의 결과 `.env`가 같은지 `diff`
   - 6단계 `printf ...; read -rs TOKEN` 줄: 입력 대역으로 값이 들어오는지 확인
   - 대화형 zsh(`zsh -f -i`, `interactivecomments` 꺼짐)에 2단계 첫 블록을 넣어 `#` 관련 오류가 없는지 확인
- 실제 배포는 PR이 dev에 머지된 뒤 CD가 실행한다(첫 배포). 머지 전에 GitHub 시크릿 6개 등록이 필요하다(사람 작업, 범위 밖)

## Deviation Log
> implement 스킬이 구현 중 계획을 벗어난 지점을 여기에 기록한다. (작성 시점엔 비워둔다)
- 검증 6: 변경 후 블록 외에 변경 전 2단계 첫 블록(`origin/dev`)도 대화형 zsh(`zsh -f -i`)에 넣어 비교 — 이유: 점검 6의 위험을 실제로 재현해 수정 효과를 보이기 위해. 결과: 변경 전은 `cp: 지운다: Not a directory`(백업 없음), `.env` 마지막 줄 뒤에 주석 문장 기록, `add_env: command not found: #`. 변경 후는 오류 없음, 백업 1개, 개행 보정
