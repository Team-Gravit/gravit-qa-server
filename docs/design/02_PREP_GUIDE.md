# Gravit QA 플랫폼 — 준비 가이드 (A-2 자격 증명 · 인프라 · A-4 콘텐츠)

> 작성일: 2026-10-15
> 비밀값(토큰·키)은 이 문서에 적지 않는다. 발급 후 서버 `.env` 또는 GitHub Secrets에만 보관한다.

---

## Part 1. 자격 증명 발급 방법 (A-2)

### 1-1. Figma Personal Access Token (#6)

**발급 절차** (Figma 공식 문서 기준)
1. Figma 파일 브라우저 좌상단 계정 메뉴 → **Settings**
2. **Security** 탭 → **Personal access tokens** → **Generate new token**
3. 만료 기간과 **scope**를 설정한다. 파일 내용·이미지 **읽기 권한만** 선택한다(정확한 scope 명칭은 Figma Scopes 문서에서 확인).
4. **Generate token** 후 즉시 복사한다. 토큰은 한 번만 표시된다.

**주의**
- 토큰은 **발급한 사람의 계정 권한**을 그대로 따른다. 그 계정이 QA 대상 Figma 파일을 볼 수 있어야 한다.
- 개인 계정으로 발급하면 담당자가 바뀔 때 연동이 끊긴다. 가능하면 **팀 공용 계정**으로 발급한다.
- 만료일을 정했다면 갱신 일정을 캘린더에 등록한다.

### 1-2. Notion Integration 토큰 (#7)

**토큰**: gravit-chatbot이 쓰는 Integration 토큰(`NOTION_TOKEN`)을 그대로 재사용한다. chatbot 배포 환경(Vercel 환경변수)에서 확인할 수 있다.

**페이지 연결** (필수)
- Notion Integration은 **명시적으로 연결된 페이지만** 읽을 수 있다.
- QA 관련 상위 페이지(Part 3의 "QA" 페이지)를 열고 우상단 **`•••` → Connections** → 해당 Integration을 추가한다.
- 상위 페이지에 연결하면 하위 페이지와 DB에도 접근 권한이 적용된다.

### 1-3. GitHub 토큰 (#8)

**권장: Fine-grained Personal Access Token, 공개 저장소 읽기 전용**
1. GitHub → **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**
2. Repository access: **Public repositories (read-only)**
3. 만료일 설정 후 생성

**이 방식이 충분한 이유**
- Team-Gravit 저장소는 모두 public이라 읽기 권한이 따로 필요 없다.
- 그래도 토큰을 쓰는 이유는 **API 호출 한도** 때문이다. GitHub REST API는 인증 없이 시간당 60회, 인증 시 시간당 5,000회까지 허용한다.

**나중에 저장소를 private로 전환하는 경우**
- 조직 승인이 필요한 토큰 또는 **GitHub App**으로 바꿔야 한다.
- GitHub App은 개인 계정에 묶이지 않으므로 장기 운영에 유리하다.

### 1-4. litellm-gw 키 (#9) — 결정: 기존 게이트웨이 공유(A안)

> **확인 결과 (2026-10-16)**: 게이트웨이 = `litellm` 컨테이너(`codex-lb-tailscale` 사이드카), config `/home/serverking/gravit/llm-api/litellm_config.yaml`에 `master_key`만 있고 DB 없음 → **virtual key 불가, master key 사용**. 모델 `codex-terra` 등록 확인.

**구조 (사실)**
- 경로: LiteLLM 별칭 `codex-terra` → codex-lb → ChatGPT 구독 계정 1개 (gravit-server `.claude/spec/service-policy/interview.md`)
- 과금: 토큰당 API 요금이 아니라 구독 사용 한도를 면접 채점·음성 변환과 **나눠 쓴다**
- 따라서 `max_budget`(USD)은 보호 수단이 되지 않는다. 보호는 `rpm_limit`(분당 요청 수)와 gravit-qa-server 쪽 동시 호출 1로 한다

**필요한 값**

| 값 | 얻는 방법 | 비밀 여부 |
|---|---|---|
| base-url | `http://litellm-gw:4000` (gravit-server 설정과 동일) | 아님 |
| 모델 별칭 | `codex-terra` (아래 3단계로 실재 확인) | 아님 |
| 키 | 아래 절차. 결과는 서버 `.env`의 `LITELLM_API_KEY`에만 둔다 | **비밀** |

**절차** (앱센터 서버 SSH 접근 권한이 있는 사람이 수행)
1. 게이트웨이 컨테이너 찾기: `docker ps --filter network=llm-net --format '{{.Names}}'`
2. DB 연결 여부와 마스터 키 위치 확인: `docker exec <컨테이너명> printenv DATABASE_URL LITELLM_MASTER_KEY`
   - 출력이 없으면 config 파일에 있을 수 있다: `docker inspect <컨테이너명> --format '{{json .Mounts}}'`로 config 경로를 찾아 `general_settings`의 `master_key`, `database_url` 확인
3. 모델 별칭 확인: `docker run --rm --network llm-net curlimages/curl -s http://litellm-gw:4000/v1/models -H "Authorization: Bearer <마스터키>"`
4. 키 결정
   - **DB 있음** → 전용 virtual key 발급(예산 없이 rpm만):
     ```bash
     docker run --rm --network llm-net curlimages/curl -s -X POST http://litellm-gw:4000/key/generate \
       -H "Authorization: Bearer <마스터키>" -H 'Content-Type: application/json' \
       -d '{"key_alias":"gravit-qa","models":["codex-terra"],"rpm_limit":5}'
     ```
   - **DB 없음** → virtual key를 만들 수 없다. 기존 `LITELLM_MASTER_KEY` 값을 그대로 쓴다

**주의**
- GitHub Secrets에 등록된 `LITELLM_MASTER_KEY`는 등록 후 값을 다시 볼 수 없다. 값은 서버(게이트웨이 설정)에서 확인하거나 게이트웨이를 만든 사람에게 받아야 한다
- 키 값은 채팅·문서·저장소에 남기지 않는다
- `rpm_limit` 5는 시작값이다. QA 준비는 기능당 1회(+재요청 최대 2회)만 호출하므로 파일럿(C6)에서 조정한다

### 1-5. 증적 저장소 (#10)

- **로컬 Docker volume으로 시작**한다. S3 키 발급은 불필요하다.
- 필수 조건: 보존 기간(예: N일)과 용량 한도를 정하고, 오래된 증적을 지우는 정리 작업을 둔다. dev·prod와 같은 디스크를 쓰기 때문이다.

---

## Part 2. 인프라 값 템플릿 (준비되는 대로 기입)

| # | 항목 | 값 | 비고 |
|---|---|---|---|
| 11 | Postgres 호스트 / 포트 | | 기존 인스턴스 |
| 11 | QA 전용 DB 이름 | | 예: `gravit_qa` |
| 11 | QA 전용 DB 계정 | | 비밀번호는 `.env`에만 |
| 12 | 배포 경로 | | 예: `/home/serverking/gravit/qa` |
| 12 | qa-api 컨테이너 자원 한도 (cpus / memory) | | |
| 12 | e2e 워커 컨테이너 자원 한도 (cpus / memory) | | Chromium 실행 고려 |
| 12 | e2e 동시 실행 수 | | 시작값 1 권장 |
| 12 | 증적 볼륨 경로 / 보존 기간 / 용량 한도 | | |
| 13 | gravit-qa 도메인 | | 예: `qa-api.…inuappcenter.kr` |
| 13 | HTTPS 인증서 방식 | | 기존 dev 도메인과 동일 방식 |
| 14 | gravit-qa 저장소 URL | | Team-Gravit 조직 |
| 14 | Docker Hub 이미지 이름 | | 기존 `DOCKER_HUB_USERNAME` 계정 |
| 9 | 게이트웨이 DB 유무 / 키 종류(virtual·master) / rpm | | 키 값은 `.env`에만 |
| — | prod `/api/v1/admin/me` 주소 | | admin-web prod 전환 후 인증 판정 대상 |
| — | dev 테스트 API 주소 | | `https://grav-it-dev.inuappcenter.kr` |

---

## Part 3. 콘텐츠 준비 가이드 (A-4)

### 3-1. Notion DB 2개 생성 (#18)

**구조**
```
[페이지] QA  ← Integration 연결
 ├ [DB] 기능 명세   (화면 ID 1개 = 행 1개)
 └ [DB] 스프린트    (스프린트 1회 = 행 1개)  ── relation ──▶ 기능 명세
```

**① 기능 명세 DB 속성**

| 속성 | Notion 타입 | 예시 / 값 |
|---|---|---|
| 화면 ID | Title | `L.7.1` (Figma 프레임명과 동일, 3-3 참고) |
| 이름 | Text | 유닛 상세 |
| 파트 | Multi-select | 서버 / 웹 / 앱 |
| 라우트 | Text | `/learning/units/$unitId` |
| 관련 API | Text | OpenAPI operationId 목록 |
| Figma | URL | 프레임 링크 |
| 명세 상태 | Select | 역문서화 초안 / 확인 필요 / 확정 |
| 최종 검증일 | Date | 마지막 QA 통과일 |

**기능 명세 본문 템플릿** (DB 템플릿으로 등록)
```
## 목적
## 진입 조건
## 주요 동작
## 상태별 UI (로딩 / 빈 상태 / 에러)
## API 및 예외 처리
## 엣지 케이스
## 변경 이력
```

**② 스프린트 DB 속성**

| 속성 | Notion 타입 | 비고 |
|---|---|---|
| 이름 | Title | 예: `2026-S21` |
| 기간 | Date (범위) | |
| 대상 기능 | Relation → 기능 명세 | 이번에 개발·수정한 기능 |
| 변경 요약 | Text | 기능별 변경 내용 |
| Figma QA 페이지 | URL | |
| QA 기준점 | Text | gravit-qa가 자동 기록(사람은 비워 둠) |

### 3-2. 기능 명세 역문서화 (#19)

| 단계 | 작업 | 담당 | 산출물 |
|---|---|---|---|
| 1 | **화면 ID 목록 확정**: `gravit-web/docs/implementation-status.md`(Figma 화면 인벤토리) + `routes.md` + AOS 화면 라우트 + 화면 없는 서버 기능(알림·리그 정산 등) | 서버(AI 작업 가능) | 기능 명세 DB의 빈 행 |
| 2 | **화면별 초안 작성**: 코드·OpenAPI·`fe-implement-spec`·Figma를 근거로 작성. 항목마다 **출처 표기**. 근거가 없거나 원천끼리 충돌하면 추측하지 않고 "확인 필요"로 남김 | 서버(AI 작업 가능) | 행별 본문, 상태 = 역문서화 초안 |
| 3 | **불일치 목록 정리**: 코드 ≠ Figma, 코드 ≠ 기존 Notion | 서버(AI 작업 가능) | "확인 필요" 목록 |
| 4 | **PM 검토**: 사용 빈도가 높은 화면부터(메인 → 학습 → 퀴즈 → 리그 → 마이) | PM | 상태 = 확정 |

**진행 팁**
- 한 번에 화면 5~10개 단위로 나눠 진행하면 검토 부담이 줄어든다.
- **파일럿(C6)에 쓸 화면부터 먼저 확정**하면, 전체 완료 전에도 QA를 시작할 수 있다.

### 3-3. Figma 규칙 (#20) — 2026-10-17 개정

기준: gravit-web `docs/fe-implement-spec/README.md` §4 (화면 ID 체계, 2026-09-01 확정). **QA 페이지는 이 체계를 그대로 따른다.**

**페이지 구조**
```
QA (페이지)
 └ Section "2026-S{n}"            ← 스프린트명 = Notion 스프린트 DB의 이름
    ├ Section "WEB"                ← 플랫폼은 이름 접미사가 아니라 Section으로 구분
    │  └ Section "{구역}"          ← MAIN & ONBOARDING / LEARNING / LEAGUE / MY / OTHER / INTERVIEW SIMULATOR
    │     └ Frame "L.7.1"          ← 화면 ID
    └ Section "AOS"
       └ (동일)
```

**프레임(화면) 이름**

| 규칙 | 내용 |
|---|---|
| 형식 | `{접두어}.{번호}[.{번호}][-{번호}]`. 접두어 `O` `M` `L` `LG` `MY` `A` `E` `W` `R` (예: `L.7.1`, `MY.2-1`) |
| 플랫폼 | 같은 화면이면 WEB·AOS에서 **같은 ID**. 구분은 상위 Section(`WEB`/`AOS`) |
| 상태 변형 | **같은 ID의 프레임을 여러 개** 둔다(예: `LG.2` 2개 = 브론즈·실버). 접미사를 붙이지 않는다 |
| ID를 확정할 수 없는 프레임 | `미확인/{구역}-{순번}`으로 두고 목록으로 보고한다. 추측해 ID를 붙이지 않는다 |
| 크기 기준 | WEB 1920×1080, AOS 360×740 (세로로 긴 화면은 폭만 맞음) |

**프레임 안의 레이어 이름**

| 대상 | 규칙 | 예 |
|---|---|---|
| 자동 생성 이름(`Frame 123…`, `Group 4`, `Rectangle 12`, `image 46`) | 역할을 나타내는 영문 kebab-case로 바꾼다 | `header`, `unit-card`, `cta-button`, `progress-bar` |
| 이미지 | `img/{내용}` | `img/planet-earth` |
| 아이콘 | `icon/{이름}` | `icon/arrow-left` |
| 반복 항목 | `{역할}-item`, 묶음은 `{역할}-list` | `unit-item`, `unit-list` |
| 컴포넌트 인스턴스 | **바꾸지 않는다** (원본 컴포넌트 이름을 따른다) | — |
| 텍스트 레이어 | 바꾸지 않는다 (내용이 곧 이름) | — |

**지키는 것**
- 이름 변경과 Section 정리만 한다. 위치·크기·스타일·계층(부모-자식)은 바꾸지 않는다
- 숨김·빈 레이어는 지우지 않고 목록으로 보고한다
- QA 페이지의 프레임은 원본과 연결되지 않는 **검수 기준 스냅샷**이다. 원본(Screen Design) 페이지는 건드리지 않는다

### 3-4. QA 메뉴 화면 시안 (#21)

| 화면 | 핵심 요소 |
|---|---|
| 스프린트 QA 목록 | 회차, 기간, 파트별 진행률(서버·웹·앱) |
| QA 준비하기 | 실행 버튼, 단계별 진행 상태, 자동/수동 분류 확인·확정 |
| 체크리스트 | 기능 행 × 파트(서버·웹·앱) 체크박스, FAIL 코멘트·스크린샷, 자동 결과 표시 |
| 리포트 | 자동 실행 결과·증적, 코드 검수 리포트, 미해결 FAIL 목록 |

- 기존 백오피스 규칙(데스크톱 1280px 이상, 라이트 모드, DS-01 토큰)을 따른다.
- 시안이 없으면 admin-web 인테이크 문서(`QA_SPEC.md`)에 레이아웃을 정의해 진행할 수 있다.
