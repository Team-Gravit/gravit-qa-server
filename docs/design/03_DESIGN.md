# Gravit QA 플랫폼 — 설계 문서 (B-1)

> 작성일: 2026-10-16
> 선행 문서: `01_QA_PLATFORM_CONTEXT.md`, `02_PREP_GUIDE.md`
> 함께 보는 문서: `04_gravit-qa-openapi.yaml` (API 초안)
> 표기: **[사실]** 저장소에서 2026-10-16에 다시 확인한 값(출처 경로 병기) / **[결정]** 대화에서 확정 / **[설계]** 이 문서의 설계 선택(C1 리뷰 대상) / **[확인 필요]** 미결

---

## 0. 확정 사항 반영 현황

| # | 항목 | 결정 | 반영 위치 |
|---|---|---|---|
| A-1 (1) | QA 기준점 | 각 저장소 `release` 브랜치 커밋 SHA | 4-2, 5-2 |
| A-1 (2) | AOS 빌드 버전 | **A안**: release마다 `versionCode` 수동 증가, gravit-qa가 release 브랜치 값 기록 | 4-2, 5-2 |
| A-1 (3) | 체크 셀 | **4상태**(미검수 / PASS / FAIL / 해당 없음), 스크린샷·코멘트 없음 | 4-5, 6-3 |
| A-1 (4) | 자동/수동 분류 | 5개 기준 모두 충족 시 자동 | 5-2 |
| A-1 (5) | 테스트 데이터 | **페르소나 데이터셋 적재 + userId 토큰 발급**, 서버 테스트 API로 적재 | 9 |
| — | 시즌 종료 직전 상태 | 전역 상태이므로 **수동 영역** | 9-4 |

### 0-1. C1 리뷰 결정 (2026-10-16)

| # | 항목 | 결정 |
|---|---|---|
| C1-1 | qa-api / qa-e2e 배포 단위 | 같은 이미지, 프로필로 분리 |
| C1-2 | RN 셸 안의 웹 화면 판정 | `WEB` 칸. `APP` 칸은 셸 고유 동작만 |
| C1-3 | 확정 후 범위 변경 | 재준비 불가, 다음 스프린트 |
| C1-4 | 자동 분류 조건 | 기준 5개 + 테스트 카탈로그 존재. 없으면 수동 + 자동화 후보 |
| C1-5 | 자동 실행 시작 | 확정 시 자동 + 수동 재실행 |
| C1-6 | 종료 조건 | 미검수 0, FAIL 허용 |
| C1-7 | 자동 판정 셀 수동 변경 | 불가 |
| C1-8 | 불안정 테스트 | 1회 재실행, 통과 시 PASS + flaky |
| C1-9 | 성공 증적 | 최종 스크린샷 1장 |
| C1-10 | 증적 보존 | 30일 |
| C1-11 | 용량 한도 도달 | 오래된 것부터 삭제, 부족하면 실행 거부 |
| C1-12 | 작업 재시도 | 최대 3회, 2→4→8분 |
| C1-13 | LLM 출력 재요청 | 2회 |
| C1-14 | 수동 페르소나 재적재 | 확정 시 1회 + 수동 버튼 |
| C1-15 | gravit-qa-server 저장소 | 골격을 먼저 만들어 전달, 사용자가 저장소 생성 후 업로드 |
| C1-16 | LLM 경로 | 기존 litellm-gw 공유(A안). 같은 구독 계정 한도를 면접 채점과 나눠 씀 |
| C1-17 | 화면 ID 체계 | gravit-web fe-implement-spec §4(Figma 프레임명)를 그대로 따름. 플랫폼은 Section, 상태 변형은 같은 이름 반복 |

---

## 1. 용어

| 용어 | 정의 |
|---|---|
| 스프린트 | QA 1회차. Notion 스프린트 DB의 행 1개와 1:1 |
| 기능 | 화면 ID 단위(`L.7.1` 등, Figma 프레임명 = gravit-web fe-implement-spec §4 체계). Notion 기능 명세 DB의 행 1개와 1:1 |
| 체크 항목 | 기능 1개에 속한 검수 항목. LLM이 초안을 만들고 사람이 확정 |
| 파트 | `SERVER` / `WEB` / `APP`. 체크 항목 × 파트 = 체크 셀 |
| 기준점 | 스프린트 QA 시점의 저장소별 release 브랜치 커밋 SHA 묶음 |
| 스냅샷 | 준비 시점에 수집한 Figma·Notion·diff·OpenAPI 원본 |
| 셋업 작업 | [QA 준비하기]가 만드는 비동기 작업(수집 → 해석 → 검증) |
| 자동 실행 | 자동 분류된 체크 항목을 결정적 테스트로 실행한 1회 |
| 증적 | 자동 실행이 남긴 파일(스크린샷·trace·HTTP 로그) |
| 페르소나 | QA용 사전 정의 유저 상태. 데이터셋으로 적재 |

**파트 구분 기준** [결정 C1-2]
- `WEB` = `gravit-web/apps/web`
- `APP` = `gravit-aos`(네이티브) + `gravit-web/apps/native`(RN WebView 셸)의 셸 고유 동작
- RN 셸 안에 표시되는 웹 화면 자체는 `WEB`과 같은 코드이므로, 모바일 뷰포트 브라우저 검증은 `WEB` 셀에서 처리하고 셸 고유 동작(뒤로가기·딥링크·OAuth)은 `APP` 셀(수동)에서 처리한다.

---

## 2. 구성요소와 책임

```
 [admin-web QA 메뉴] ──/qa-api/*──▶ [qa-api] ──────────▶ Postgres(gravit_qa)
        │                              │  ▲  작업 테이블(SKIP LOCKED)
        │ (polling)                    │  │
        │                     ┌────────┘  └───────┐
        │                     ▼                   │
        │             [셋업 작업 실행기]       [qa-e2e 워커] ──▶ dev 웹 / dev API
        │              │ Figma REST              │  Playwright(Chromium)
        │              │ Notion REST             │  증적 → volume
        │              │ GitHub REST             │
        │              │ dev OpenAPI             └─▶ gravit-server 테스트 API
        │              └ litellm-gw (llm-net)          (픽스처 적재·토큰)
        └──────── 인증 판정: GET {AUTH_HOST}/api/v1/admin/me
```

> 명칭: 서비스명은 **gravit-qa**, 저장소·빌드 산출물·Docker 이미지는 **gravit-qa-server** (`Team-Gravit/gravit-qa-server`). Java 패키지는 `gravit.qa`.

| 구성요소 | 책임 | 하지 않는 것 |
|---|---|---|
| admin-web QA 메뉴 | 스프린트 목록·준비·체크리스트·리포트 화면, 진행 상태 polling | 판정 로직 |
| **qa-api** (컨테이너 1) | REST API, 인증, 상태 전이, 셋업 작업 실행(수집·LLM 해석·스키마 검증), 체크 결과 리뷰 수집, 증적 서빙, 정리 작업 | 브라우저 실행 |
| **qa-e2e** (컨테이너 2) | 자동 실행 작업 소비: 픽스처 재적재 → 토큰 발급 → Playwright 테스트 → 결과·증적 기록 | API 제공, LLM 호출 |
| gravit-server (기존) | 테스트 API: 페르소나 데이터셋 재적재, userId 토큰 발급 | QA 상태 보관 |
| 각 저장소 CI (C8) | lint·detekt·커버리지·LLM PR 리뷰 실행 | — |
| litellm-gw (기존) | LLM 프록시. 뒤는 codex-lb → ChatGPT 구독 계정 1개(면접 채점과 한도 공유) [결정 A안] | — |

[결정 C1-1] qa-api와 qa-e2e는 **같은 이미지, 다른 Spring 프로필**(`api` / `e2e`)로 기동한다. 작업 테이블을 공유하고 작업 유형으로 소비 대상을 나눈다. Chromium 의존성 때문에 이미지 크기가 커지는 것이 문제가 되면 C9에서 이미지를 분리한다.

---

## 3. 외부 값 (재확인 결과)

| 항목 | 값 | 출처 |
|---|---|---|
| 관리자 판정 엔드포인트 | `GET /api/v1/admin/me`, `hasRole("ADMIN")` | [사실] `gravit-server` `security/config/SecurityConfig.java:63`, `admin/controller/AdminMeController.java` |
| `/admin/me` 응답 | `adminId`, `nickname`, `email`, `profileImgNumber` | [사실] `admin/dto/response/AdminMeResponse.java` |
| 판정 패턴 원본 | 타임아웃 5초, 성공만 300초 캐시, 캐시 키 sha256(token), 200/401/403/그 외→503 | [사실] `gravit-chatbot` `api/gravit_auth.py` |
| 테스트 API 보호 | `/api/v1/test/notifications/**` authenticated, `/api/v1/test/**` permitAll | [사실] `SecurityConfig.java:61-62` |
| 테스트 컨트롤러 프로필 | 6개 컨트롤러 모두 `@Profile("!prod")` | [사실] `test/**/*Controller.java` |
| userId 토큰 발급 | `POST /api/v1/test/users/login?userId=` | [사실] `test/user/TestUserCheatCreateController.java:121-128` |
| dev OpenAPI | `https://grav-it-dev.inuappcenter.kr/v3/api-docs` (Swagger 경로 permitAll) | [사실] `SecurityConfig.java:56`, `gravit-web/apps/web/.env.example` |
| dev 서버 배포 경로 | `/home/serverking/gravit/dev` | [사실] `.github/workflows/cd-dev.yml:102,115` |
| dev compose 네트워크 | `gravit-dev`(bridge), `llm-net`(external) | [사실] `docker-compose-dev.yml` |
| Postgres | compose 외부 인스턴스(`POSTGRESQL_DEV_URL`) | [사실] `application-dev.yml:2-5` |
| 스키마 관리 | Flyway, `classpath:db/migration`, 현재 `V47` | [사실] `application-dev.yml:53-60`, `db/migration/` |
| 기존 시드 규약 | `sql/seed/`는 DEV 전용·수동 실행, 자연키 접두어로 식별·정리, 온보딩 부속 행(`user_league`·`mission`·`learning`) 함께 생성 | [사실] `src/main/resources/sql/README.md`, `sql/seed/dev_seed_users.sql` |
| 시간 처리 | 시간 의존 코드가 모두 `Clock` 빈 사용(KST) | [사실] `global/config/TimeConfig.java` |
| Redis 보관 상태 | 리그 랭킹, 시즌 종료 캐시, 시즌 팝업 노출, 토큰, 재시도 큐 | [사실] `RedisLeagueRankingStore` 외 |
| admin-web 프록시 패턴 | `vercel.json` rewrites: `/ravvit-api/:path*` → chatbot, 마지막에 SPA catch-all | [사실] `gravit-admin-web/vercel.json` |
| 웹 토큰 저장 | `localStorage`의 `accessToken`, `refreshToken` | [사실] `gravit-web/apps/web/src/entities/auth/model/auth-storage.ts` |
| AOS 버전 | `versionCode = 1`, `versionName = "1.0.0"` | [사실] `gravit-aos/app/build.gradle.kts` |
| AOS 강제 업데이트 | `BuildConfig.VERSION_NAME` vs `GET /api/v1/version`(`app.version`) | [사실] `SplashActivity.kt:103-105`, `VersionController.java` |
| release 브랜치 | **4개 저장소 모두 없음** | [사실] `git ls-remote --heads` (server·web·aos·admin-web) |
| 저장소 CI | server: `ci-common.yml`(테스트) / web: `storybook.yml`만 / aos·admin-web: 워크플로 없음 | [사실] 각 저장소 `.github/workflows/` |

---

## 4. 데이터 모델

Postgres(`gravit_qa` DB, [확인 필요] 이름 확정). Flyway로 관리한다. 시각은 gravit-server 마이그레이션 규칙에 맞춰 `TIMESTAMP(6)`(KST 기준)으로 저장하고, API 응답에서 `+09:00` 오프셋을 붙인다. 스키마 원본은 gravit-qa-server `V1__init_qa_schema.sql`.

### 4-1. ERD 요약

```
sprint 1─1 baseline 1─N baseline_repo
sprint 1─N snapshot
sprint N─M feature   (sprint_feature)
feature 1─N checklist_item 1─N check_cell 1─N check_cell_history
sprint 1─N auto_run 1─N run_case_result 1─N evidence
sprint 1─N review_check
job (sprint_id nullable) 1─N job_step
```

### 4-2. 스프린트·기준점

**sprint**

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | bigint PK | |
| sprint_key | text UNIQUE | `2026-S21` (Notion 스프린트 Title) |
| notion_page_id | text UNIQUE | 스프린트 행 페이지 ID |
| period_start / period_end | date | Notion `기간` 사본 |
| figma_file_key | text | Notion `Figma QA 페이지` URL에서 추출 |
| status | text | 6-1 참고 |
| prepared_at / confirmed_at / closed_at | timestamptz | |
| created_by | bigint | `/admin/me`의 `adminId` |
| version | int | 낙관적 잠금 |

**baseline** (스프린트당 1개, 준비 시 생성·재준비 시 교체)

| 컬럼 | 설명 |
|---|---|
| id, sprint_id UNIQUE, created_at | |
| previous_baseline_id | 직전 **종료된** 스프린트의 기준점. 없으면 null |

**baseline_repo**

| 컬럼 | 설명 |
|---|---|
| baseline_id, repo | PK. repo ∈ `gravit-server`, `gravit-web`, `gravit-aos` |
| branch | `release` |
| commit_sha | 준비 시점 HEAD |
| previous_commit_sha | 직전 기준점의 SHA. 없으면 null(전체 범위) |
| aos_version_code / aos_version_name | `gravit-aos`만. release 브랜치 `app/build.gradle.kts`에서 정규식 추출 |
| version_code_warning | `versionCode`가 직전 기준점보다 크지 않으면 true (A안 규칙 위반 경고) |

### 4-3. 기능·스냅샷

**feature** — Notion 기능 명세 DB의 사본(준비 때마다 upsert)

| 컬럼 | 설명 |
|---|---|
| id, screen_id UNIQUE | `L.7.1` |
| name, route, parts(text[]) | |
| notion_page_id UNIQUE | |
| spec_status | `DRAFT`(역문서화 초안) / `NEEDS_CHECK`(확인 필요) / `CONFIRMED`(확정) |
| related_operation_ids (text[]) | Notion `관련 API` 파싱 |

**sprint_feature** (sprint_id, feature_id) — Notion `대상 기능` relation 사본

**snapshot**

| 컬럼 | 설명 |
|---|---|
| id, sprint_id, job_id | |
| source | `NOTION_SPRINT` / `NOTION_SPEC` / `FIGMA_FRAME` / `GITHUB_DIFF` / `OPENAPI` |
| source_ref | 페이지 ID, 노드 ID, `repo@sha..sha`, OpenAPI URL |
| content_hash | sha256 |
| storage_path | 증적 볼륨 내 경로(본문·이미지) |
| captured_at | |

[설계] 스냅샷은 **보존 정리 대상에서 제외**한다(재현성 요건). 대신 Figma 이미지는 스프린트 대상 프레임만 저장해 용량을 제한한다.

### 4-4. 체크리스트

**checklist_item**

| 컬럼 | 설명 |
|---|---|
| id, sprint_id, feature_id | |
| title, expected | 검수 항목·기대 결과(명세 근거) |
| parts (text[]) | 적용 파트. 미포함 파트의 셀은 `NOT_APPLICABLE` |
| classification | `AUTO` / `MANUAL` |
| criteria (jsonb) | 기준 5개별 `{met: bool, reason: text}` |
| automation_gap | `AUTO` 기준은 충족하나 Git 테스트 카탈로그에 대응 테스트가 없음 → 이번 스프린트는 수동 처리 |
| test_ref | 자동 항목의 테스트 카탈로그 ID(5-4) |
| persona_key | 사전 조건 페르소나(9-3) |
| source_refs (jsonb) | 근거 스냅샷 ID 목록 |
| origin | `LLM` / `HUMAN` |
| review_state | `PROPOSED` / `CONFIRMED` / `REJECTED` |
| sort_order, version | |

### 4-5. 체크 셀

**check_cell**

| 컬럼 | 설명 |
|---|---|
| item_id, part | PK |
| state | `UNCHECKED` / `PASS` / `FAIL` / `NOT_APPLICABLE` |
| source | `MANUAL` / `AUTO` |
| auto_run_id | `source = AUTO`일 때 판정한 실행 |
| updated_by, updated_at | 수동은 `adminId`, 자동은 null |
| version | 낙관적 잠금(동시 체크 충돌 시 409) |

**check_cell_history** — 셀 변경마다 1행(이전 상태, 새 상태, 변경자, 시각). 감사용, 삭제하지 않음.

### 4-6. 자동 실행·증적

**auto_run**: id, sprint_id, baseline_id, job_id, status(6-4), fixture_manifest(jsonb, 적재 결과), started_at, finished_at, counts(jsonb).

**run_case_result**: id, run_id, item_id, part, outcome(`PASS`/`FAIL`/`ERROR`/`SKIPPED`), flaky(bool), attempts, duration_ms, error_message.
- `FAIL`: 기대값 불일치(제품 결함 후보) / `ERROR`: 사전 조건·환경 실패(제품 판정 불가)

**evidence**: id, run_id, case_result_id, kind(`SCREENSHOT`/`TRACE`/`HTTP_LOG`/`CONSOLE_LOG`), storage_path, content_type, size_bytes, sha256, created_at, expires_at.

### 4-7. 리뷰 리포트

**review_check**: id, sprint_id, repo, commit_sha, check_name, status, conclusion, details_url, completed_at, collected_at. 출처는 GitHub Checks API(기준점 SHA 기준).

### 4-8. 작업 큐

**job**

| 컬럼 | 설명 |
|---|---|
| id | bigint PK |
| type | `SETUP` / `AUTO_RUN` / `REVIEW_COLLECT` / `EVIDENCE_CLEANUP` |
| sprint_id | nullable |
| status | 6-2 참고 |
| payload | jsonb |
| attempt, max_attempts | `max_attempts` 기본 4 = 최초 1회 + 재시도 3회 [결정 C1-12] |
| run_after | 재시도 대기 시각 |
| locked_by, locked_at, heartbeat_at | 워커 ID, 잠금 시각, 생존 신호 |
| cancel_requested | bool |
| last_error | text |
| created_at, finished_at | |

인덱스: `(status, type, run_after)`. 소비 쿼리:

```sql
SELECT * FROM job
WHERE status = 'QUEUED' AND type = ANY(:types) AND run_after <= now()
ORDER BY id
FOR UPDATE SKIP LOCKED
LIMIT 1;
```

[설계] `job`, `job_step`은 조건부 원자 갱신이 핵심이라 JPA 엔티티 없이 JdbcTemplate으로만 다룬다.

**job_step**: job_id, seq, name, status(`PENDING`/`RUNNING`/`DONE`/`FAILED`/`SKIPPED`), message, started_at, finished_at. [QA 준비하기] 화면의 단계별 진행 표시에 사용.

**제약** [설계]: 스프린트당 실행 중(`QUEUED`/`RUNNING`)인 `SETUP` 1개, `AUTO_RUN` 1개. 부분 유니크 인덱스로 강제한다.

---

## 5. 실행 흐름

### 5-1. 스프린트 등록

1. 관리자가 Notion 스프린트 페이지 URL을 입력한다.
2. qa-api가 Notion에서 행을 조회해 `sprint_key`·기간·Figma URL을 채우고 `DRAFT`로 생성한다.
3. 같은 Notion 페이지로 중복 등록하면 409.

### 5-2. QA 준비하기 (셋업 작업)

`POST /sprints/{id}/setup-jobs` → 202 + job. 단계(`job_step`):

| # | 단계 | 처리 | 결정성 |
|---|---|---|---|
| 1 | 기준점 확정 | GitHub API로 3개 저장소 `release` HEAD SHA 조회. 직전 기준점과 비교. `gravit-aos` release의 `build.gradle.kts`에서 `versionCode`/`versionName` 추출, 증가 여부 검사 | 결정적 |
| 2 | Notion 수집 | 스프린트 행 + `대상 기능` relation의 기능 명세 페이지 본문 | 결정적 |
| 3 | Figma 수집 | QA 페이지 → 스프린트명 Section → `WEB`/`AOS` Section(플랫폼) → 프레임명 = 화면 ID(`^(O\|M\|L\|LG\|MY\|A\|E\|W\|R)\.\d+([.-]\d+)*$`). 같은 ID가 여러 개면 상태 변형. `미확인/…`은 경고로 보고. 대상 기능 프레임 PNG 저장 (규칙: 02 가이드 3-3) | 결정적 |
| 4 | 코드 diff 수집 | GitHub compare(`previous..current`)의 변경 파일·패치 | 결정적 |
| 5 | OpenAPI 수집 | dev `/v3/api-docs`, 기능의 `관련 API` operationId만 추출 | 결정적 |
| 6 | 해석 | 기능 1개당 LLM 1회: 체크 항목 초안 + 기준 5개 판정 + 페르소나 제안 | LLM |
| 7 | 검증 | JSON 스키마 검증, 참조 무결성(파트·페르소나 키·테스트 카탈로그 ID 실존) 검사. 실패 시 오류 내용을 붙여 최대 2회 재요청 | 결정적 |
| 8 | 반영 | 기존 `PROPOSED` 항목을 한 트랜잭션에서 교체, 셀 생성, 스프린트 `REVIEW` | 결정적 |

**자동 분류 규칙** [결정 + 설계]
- LLM이 기준 5개를 판정하고, 5개 모두 `met = true`이면서 **테스트 카탈로그에 대응 테스트가 있을 때만** `AUTO`.
- 기준은 충족하나 테스트가 없으면 `MANUAL` + `automation_gap = true` → 리포트의 "자동화 후보" 목록으로 노출(C7 입력).
- 이유: "LLM은 준비·해석에만, 실행·판정은 결정적" 원칙. LLM이 테스트를 생성·실행하지 않는다.

**누락 처리**: 기능 명세가 `NEEDS_CHECK`이거나 Figma 프레임이 없으면 항목은 만들되 `expected`에 "근거 없음"을 명시하고, 리포트 경고로 노출한다. 추측으로 채우지 않는다.

### 5-3. 분류 확인·확정

- `REVIEW` 상태에서만 항목 수정(제목·기대값·파트·분류·페르소나)·추가·거절 가능.
- `POST /sprints/{id}/checklist/confirm` → 모든 항목이 `CONFIRMED`/`REJECTED`여야 함. 스프린트 `IN_PROGRESS`.
- 확정 시 `AUTO` 항목이 있으면 자동 실행 작업을 1건 큐에 넣는다(요청 파라미터로 끌 수 있음).

### 5-4. 자동 실행 (qa-e2e)

| # | 단계 | 내용 |
|---|---|---|
| 1 | 픽스처 재적재 | `POST {DEV_API}/api/v1/test/fixtures/qa/reload?set=auto` → 페르소나 매니페스트(`personaKey → userId`) 수신, `auto_run.fixture_manifest`에 저장 |
| 2 | 토큰 발급 | 실행에 필요한 페르소나만 `POST /api/v1/test/users/login?userId=` |
| 3 | 테스트 실행 | 테스트 카탈로그의 `test_ref`별 실행. 웹은 `localStorage`에 `accessToken`/`refreshToken` 주입 후 진입. API 항목은 HTTP 호출 |
| 4 | 판정 | 테스트 결과 → `run_case_result` → 해당 셀 `PASS`/`FAIL`(`source = AUTO`). `ERROR`·`SKIPPED`는 셀을 바꾸지 않음 |
| 5 | 증적 | 실패 시 스크린샷·trace·HTTP 로그, 성공 시 최종 스크린샷 1장 [결정 C1-9] |

**테스트 카탈로그** [설계]: gravit-qa-server 저장소 `e2e/` 아래 JUnit 5 + Playwright for Java 테스트. 각 테스트에 카탈로그 ID(`@QaTest("L.7.1.empty-state")`)를 붙이고, 빌드 시 카탈로그 목록을 생성해 셋업 작업이 참조한다.

**웹 대상 주소** [결정]: `https://dev.gravit.inuappcenter.kr/`. [확인 필요] 이 배포가 어느 브랜치를 반영하는지(release 기준점 SHA와 일치해야 "테스트한 코드 = 기준점 코드"가 성립).

### 5-5. 수동 체크

- `PUT /checklist-items/{itemId}/cells/{part}` (`state`, `version`).
- `AUTO` 항목의 `WEB`/`SERVER` 셀은 수동 변경 불가(재실행으로만 갱신). `NOT_APPLICABLE` 셀도 `IN_PROGRESS`에서는 변경 불가.
- 모든 변경은 `check_cell_history`에 기록.

### 5-6. 리뷰 리포트 수집

- `REVIEW_COLLECT` 작업: 기준점 SHA마다 GitHub `GET /repos/{owner}/{repo}/commits/{ref}/check-runs` 조회 → `review_check` upsert.
- 현재 CI 구성상 서버의 `공통 CI` 외에는 수집할 결과가 거의 없다([사실] 3절). C8에서 각 저장소 CI가 추가되면 자동으로 채워진다.

### 5-7. 종료

- `POST /sprints/{id}/close`: `UNCHECKED` 셀이 0개여야 한다. FAIL은 허용(리포트의 미해결 FAIL로 남음).
- 종료 시 Notion 스프린트 행의 `QA 기준점` 속성에 SHA 요약을 기록한다. Integration 쓰기 권한 부여 완료 [결정].
- gravit-chatbot 클라이언트는 조회 전용으로 차단되어 있으므로, Java 이식 시 이 속성 갱신(`PATCH /v1/pages/{id}`)만 허용 목록으로 연다.
- 기록 실패는 종료를 막지 않고 리포트 경고(`NOTION_WRITE_FAILED`)로 남긴다.

---

## 6. 상태 전이

### 6-1. 스프린트

```
DRAFT ──준비──▶ PREPARING ──성공──▶ REVIEW ──확정──▶ IN_PROGRESS ──종료──▶ CLOSED
                   │  ▲                │
                   ▼  │재시도           └──재준비──▶ PREPARING (PROPOSED 항목 교체)
              PREPARE_FAILED
```

| 전이 | 조건 |
|---|---|
| DRAFT/PREPARE_FAILED/REVIEW → PREPARING | 실행 중 SETUP 작업 없음 |
| IN_PROGRESS → PREPARING | **불가**. 확정 이후 범위가 바뀌면 다음 스프린트로 처리 [결정 C1-3] |
| REVIEW → IN_PROGRESS | 모든 항목 CONFIRMED/REJECTED |
| IN_PROGRESS → CLOSED | UNCHECKED 셀 0, 실행 중 AUTO_RUN 없음 |

### 6-2. 작업

```
QUEUED ──잠금──▶ RUNNING ──▶ SUCCEEDED
   ▲                │
   │ 재시도 가능     ├──▶ FAILED (attempt = max_attempts 또는 재시도 불가 오류)
   └── run_after ───┤
                    └──▶ CANCELLED (cancel_requested 확인 시)
```
- heartbeat 미갱신(기본 2분 [설계]) 작업은 정리 스케줄러가 `QUEUED`로 되돌린다(attempt +1).

### 6-3. 체크 셀

```
UNCHECKED ⇄ PASS ⇄ FAIL     (UNCHECKED ⇄ FAIL 직접 전이 허용)
NOT_APPLICABLE              (REVIEW 단계에서 parts로 결정, 이후 고정)
```

### 6-4. 자동 실행

`QUEUED → RUNNING → COMPLETED | FAILED | CANCELLED`
- `FAILED` = 픽스처 적재·토큰 발급 등 실행 자체 실패(케이스 판정 없음).
- 케이스 일부가 FAIL이어도 실행은 `COMPLETED`.

---

## 7. 인증·인가

### 7-1. 관리자 인증 [결정]

| 항목 | 내용 |
|---|---|
| 방식 | 요청의 `Authorization: Bearer <accessToken>`을 `GET {AUTH_HOST}/api/v1/admin/me`로 전달, 상태코드로 판정 |
| 매핑 | 200 통과 / 401 → 401 / 403 → 403 / 타임아웃·연결 실패·그 외 → 503 |
| 캐시 | 성공만 300초, 키 = sha256(token). gravit-chatbot과 동일 값 |
| 타임아웃 | 5초 |
| 환경 | `AUTH_HOST` 설정값. 현재 dev, admin-web prod 전환 시 prod로 교체 |
| 인가 | ADMIN이면 전 기능 허용(역할 세분화 없음) |
| 감사 | 응답의 `adminId`를 변경 이력·생성자에 기록 |

### 7-2. 프론트 연동 [결정]

- admin-web `vercel.json`에 `/qa-api/:path*` → `https://{QA_DOMAIN}/api/v1/:path*` rewrite를 **SPA catch-all보다 앞**에 추가(현행 `/ravvit-api` 패턴과 동일).
- 증적 파일도 인증이 필요하므로 `<img src>` 직접 참조가 불가하다. 프론트는 `fetch` → Blob URL로 표시한다.

### 7-3. 외부 자격 증명

| 대상 | 자격 증명 | 보관 |
|---|---|---|
| Figma | PAT(읽기 scope) | `.env` |
| Notion | Integration 토큰(chatbot과 공유) | `.env` |
| GitHub | Fine-grained PAT(public read-only) | `.env` |
| litellm-gw | **기존 master key** [사실: 게이트웨이에 DB 없음 → virtual key 불가, 2026-10-16 확인] | `.env` `LITELLM_API_KEY` |
| gravit-server 테스트 API | **현재 없음(permitAll)** → #15에서 QA 서비스 키 도입 예정 | `.env` |

---

## 8. 자원 격리

| 항목 | 설계 | 값 |
|---|---|---|
| compose | 별도 프로젝트(`/gravit/qa`), dev·prod compose와 분리 | 경로 [확인 필요] |
| qa-api | `deploy.resources.limits` 필수 | cpus/memory [확인 필요] |
| qa-e2e | 동일. Chromium 실행 고려 | cpus/memory [확인 필요] |
| e2e 동시 실행 | 워커 내 동시 작업 1 | 시작값 1 |
| 네트워크 | `llm-net`(external) 참여 → `litellm-gw` 접근. `litellm-gw`는 `codex-lb-tailscale`의 네트워크 별칭이고, `litellm` 컨테이너(`ghcr.io/berriai/litellm:main-latest`)가 그 네트워크를 공유하는 사이드카. 설정 파일 `/home/serverking/gravit/llm-api/litellm_config.yaml`. 등록 모델: `codex-sol`, `codex-terra`, `codex-luna`, `gpt-transcribe` | [사실] 2026-10-16 서버 확인 |
| dev 서버 부하 | 테스트 대상이 같은 장비 → 자동 실행은 동시 1건, 케이스 순차 실행 | |
| LLM | 구독 한도를 면접 채점과 공유. 게이트웨이에서 키별 제한을 걸 수 없으므로(DB 없음) **앱에서** 동시 호출 1 + 분당 호출 상한 + 429 연속 시 중단 | 분당 상한 시작값 5 [설계] |
| 증적 볼륨 | 앱 수준 용량 한도 + **보존 30일 [결정 C1-10]**. 한도 초과 시 오래된 증적부터 삭제, 그래도 부족하면 새 실행 거부(507) [결정 C1-11] | 경로·한도 [확인 필요] |

[설계] 기본 Docker local 볼륨 드라이버는 용량 한도를 강제하지 않으므로, 한도는 `evidence.size_bytes` 합계로 앱이 관리한다. `EVIDENCE_CLEANUP` 작업을 하루 1회 실행한다.

---

## 9. 테스트 데이터 (페르소나 데이터셋)

### 9-1. 원칙 [결정]

- 페르소나를 문서로 정의하고, 그대로 만족하는 데이터셋(SQL)을 gravit-server 저장소에 둔다.
- 자동 실행마다 서버 테스트 API로 **삭제 후 재적재**하고, 반환된 userId로 토큰을 발급해 사용한다.

### 9-2. 저장·적재 규칙

| # | 조건 | 구현 |
|---|---|---|
| 1 | 상태 오염 방지 | 실행마다 페르소나 데이터 전체 삭제(FK 역순) 후 재적재, 한 트랜잭션 |
| 2 | 시간 경과 대응 | 모든 날짜는 `now() - interval '…'` 상대값 |
| 3 | 스키마 변경 대응 | 데이터셋은 `gravit-server/src/main/resources/sql/seed/qa/`에 둠. 서버 CI(Testcontainers Postgres 의존성 존재 [사실] `build.gradle`)에서 "마이그레이션 → 적재 → 매니페스트 검증" 테스트 추가 |
| 4 | ID 충돌 방지 | **기존 시드 규약을 따라 자연키 접두어 방식 채택** (아래 참고) |
| 5 | Redis 상태 | 적재 후 페르소나가 속한 리그의 랭킹을 `RedisLeagueRankingStore` 경로로 재구성. 구체 키는 C3에서 코드 기준 확정 |
| 6 | 전역 상태 | 시즌 날짜는 건드리지 않음. 시즌 종료 직전 검증은 수동 |

**조건 4 변경 사유** [결정]
- 기존 `dev_seed_users.sql`은 고정 ID를 쓰지 않고, `provider_id`·`email`·`handle`을 `seed-dev-NN` 접두어로 만들어 IDENTITY가 ID를 발급하게 한다. 정리도 접두어로 한다([사실] 3절).
- 같은 방식(`qa-persona-<key>`)을 쓰면 예약 ID 범위 관리가 필요 없고, 연관 테이블은 이메일 서브쿼리로 참조한다.
- userId는 재적재마다 바뀌지만, 적재 API가 매니페스트로 반환하므로 "ID만 알면 토큰을 받는다"는 흐름은 동일하다.

### 9-3. 페르소나 목록 (초안)

키는 `checklist_item.persona_key`로 참조한다.

| 키 | 상태 | 주요 데이터 |
|---|---|---|
| `pre-onboarding` | 온보딩 전 | `users.is_onboarded = false`, 부속 행 없음 |
| `fresh` | 온보딩 직후, 학습 이력 0 | users + user_league + mission + learning 기본값 |
| `learner` | 학습 진행 중 | 최근 14일 제출 이력(정답·오답 혼합), 오답노트, 북마크, 주간 기록 |
| `streak` | 연속 학습 N일 | `learning.consecutive_solved_days`와 일별 기록 일치 |
| `chapter-almost` | 챕터 마지막 레슨 직전 | 특정 챕터의 마지막 레슨 1개 제외 완료 |
| `social` | 팔로우 관계·피드 보유 | 맞팔로우·단방향 팔로워(보조 페르소나와 관계), 피드·축하 |
| `league-edge` | 승급/강등 경계 | 같은 리그 보조 유저들과 LP 경계 배치, 리그 이력 |
| `mission` | 미션 완료 직전 | 미션 진행도 지정 |
| `interviewer` | 면접 이력 보유 | 세션 상태별 1건씩(`IN_PROGRESS`/`GRADING`/`GRADING_FAILED`/`COMPLETED`), 요약·약점 데이터 |
| `inquirer` | 문의 답변 완료 | 문의 + 관리자 답변 |
| `withdrawing` | 탈퇴 유예 중 | 탈퇴 요청 상태(복구 흐름 검증). 유예 정리 예약이 Redis(`RedisUserCleanManager`)에 있으므로 9-2 #5 대상 |
| `suspended` | 정지 | `status = SUSPENDED` |
| `admin` | 관리자 | `role = ADMIN` |

보조 유저(`qa-persona-aux-NN`)는 소셜·리그 관계를 위한 상대방이며 토큰을 발급하지 않는다.

### 9-4. 수동으로 남는 영역

| 영역 | 이유 |
|---|---|
| 시즌 종료 직전·시즌 정산 | 시즌이 전역 엔티티 |
| AOS 네이티브 화면 전체 | 자동화 도구 없음 |
| **AOS에서 페르소나 계정 사용** | [결정] QA 시 소셜 로그인을 걷어내고 토큰을 주입한다. 세부(빌드 방식·주입 경로)는 9-6 |

### 9-6. 수동 QA의 토큰 주입 [결정 + 설계]

| 항목 | 설계 |
|---|---|
| 웹 | 브라우저 `localStorage`에 `accessToken`/`refreshToken` 주입 |
| AOS | 소셜 로그인 대신 토큰을 입력받는 경로를 둔 QA용 빌드. [설계] release 코드와 같은 SHA에서 **빌드 타입(또는 flavor)으로 분리**해야 기준점 일치(A안 versionCode 기록)가 유지된다 |
| 페르소나 세트 분리 | [결정] 자동 실행은 매번 재적재하므로, 수동 테스터가 같은 페르소나를 쓰면 검수 도중 데이터가 지워진다 → 세트를 `auto` / `manual`로 분리(`qa-persona-{set}-{key}`), 재적재 API가 세트를 인자로 받음 |
| 수동 세트 재적재 | 스프린트 확정 시 1회 + 관리자가 필요 시 수동 실행 [결정 C1-14] |
| 토큰 전달 | [결정] 백오피스 QA 메뉴에서 페르소나별 토큰 발급·복사 (gravit-qa가 `users/login` 대리 호출). `manual` 세트만 허용 |

**필요 판단 근거**
- 스프린트 `IN_PROGRESS` 동안 자동 실행과 수동 체크가 동시에 진행된다(5-3, 5-4). 세트를 나누지 않으면 자동 실행의 재적재가 수동 테스터의 데이터를 지운다.
- #15로 테스트 API에 서비스 키가 걸리면 테스터가 토큰을 직접 발급받을 수 없으므로, 키를 가진 gravit-qa가 대리 발급해야 한다.

**API** (OpenAPI `Persona` 태그)
- `GET /personas?set=manual` — 마지막 적재 매니페스트
- `POST /personas/manual/reload` — 수동 세트 재적재(동기)
- `POST /personas/manual/{personaKey}/tokens` — 토큰 발급

### 9-5. gravit-server 변경 목록

| # | 변경 | 비고 |
|---|---|---|
| S1 | `POST /api/v1/test/fixtures/qa/reload?set={auto\|manual}` (`!prod`) | 삭제·적재·Redis 재구성을 한 요청으로, 매니페스트 반환. 세트별 독립 |
| S2 | `sql/seed/qa/` 데이터셋 + 페르소나 명세 문서 | `sql/README.md` 표에 폴더 추가 |
| S3 | 적재 검증 테스트(CI) | 마이그레이션 변경 시 데이터셋 깨짐 조기 발견 |
| S4 | `users/login`의 미존재 userId 응답 정리 | 현재 `findById(...).get()` 예외 → 명시적 404 권장 |
| S5 | #15 테스트 API 접근 제한 | 설계 후 리마인드 항목 |

---

## 10. 실패·재시도 정책

| 대상 | 재시도 대상 | 방식 | 재시도 불가(즉시 실패) |
|---|---|---|---|
| Notion | 429, 5xx, 타임아웃 | 429는 `Retry-After` 준수(chatbot 방식), 그 외 지수 백오프 최대 3회 | 401·403·404 |
| Figma | 429, 5xx, 타임아웃 | `Retry-After` 있으면 준수, 없으면 지수 백오프 최대 3회 | 403·404 |
| GitHub | 5xx, 2차 한도(403/429 + `retry-after`/`x-ratelimit-reset`) | 헤더 시각까지 대기 후 재시도 | 404(브랜치 없음 → 준비 실패 사유로 표시) |
| dev OpenAPI | 5xx, 타임아웃 | 백오프 3회 | — |
| LLM | 5xx, 429, 타임아웃 | 백오프 3회. 429 연속 시 즉시 중단(구독 계정 잠김 방지) | 401·403(키 문제 → 준비 실패, 사유 노출) |
| LLM 출력 | 스키마·참조 무결성 실패 | 오류를 붙여 재요청 최대 2회 | 3회째 실패 시 해당 기능만 "초안 생성 실패" 항목 1개로 남기고 계속 |
| 픽스처 재적재 | 5xx, 타임아웃 | 1회 재시도 | 4xx → 실행 `FAILED` |
| 테스트 케이스 | 실패 | 1회 재실행. 재실행 성공 시 `PASS` + `flaky = true` | `ERROR`는 재실행하지 않음 |
| 작업 전체 | 위 정책으로도 실패 | `attempt < max_attempts`면 `run_after = now() + 2^attempt 분` 재큐 | 재시도 불가 오류 |

**멱등성**
- 셋업 작업은 처음부터 다시 실행하고, 결과는 마지막 단계(5-2 #8)에서만 한 트랜잭션으로 교체한다. 중간 실패 시 기존 항목은 그대로 남는다.
- 자동 실행은 실행 단위로 결과를 쌓고, 셀 반영은 케이스 완료 시점마다 수행한다(부분 결과 보존).

---

## 11. gravit-qa-server 저장소 구조 (초안)

```
gravit-qa-server/
├ .claude/                 # gravit-server 하네스 이식
├ src/main/java/.../qa/
│  ├ sprint/  checklist/  run/  evidence/  review/  job/
│  ├ auth/                 # /admin/me 판정 필터 + 캐시
│  └ adapter/              # figma, notion, github, openapi, llm, gravitserver
├ src/main/resources/
│  ├ db/migration/         # Flyway
│  ├ prompts/              # LLM 프롬프트(Git 관리)
│  └ schemas/              # LLM 출력 JSON 스키마
├ e2e/                     # Playwright for Java 테스트 = 테스트 카탈로그
└ docker-compose-qa.yml
```

---

## 12. 사전 조건 및 미결

| # | 항목 | 상태 | 영향 |
|---|---|---|---|
| P1 | 3개 저장소 `release` 브랜치 생성 | **없음** [사실] | 없으면 5-2 #1 실패 |
| P2 | AOS release마다 `versionCode` 증가 규칙 공지 | A안 결정, 운영 시작 필요 | 미준수 시 경고만 표시 |
| P3 | Notion Integration 쓰기 권한 | **부여 완료** | 5-7 Notion 기록 |
| P4 | dev 웹 배포 주소와 release 반영 여부 | 주소 확정(`dev.gravit.inuappcenter.kr`). release 브랜치 생성 후 배포 브랜치를 맞춘다. 그 전까지 자동 실행 결과는 기준점 SHA와 불일치할 수 있음을 **감수 [결정]** | 5-4 자동 실행 대상 |
| P5 | AOS 수동 QA 시 페르소나 로그인 방법 | 소셜 로그인 제거 + 토큰 주입 [결정], 세부 9-6 | 9-4, 9-6 |
| P6 | 인프라 값(DB, 경로, 자원 한도, 도메인, 볼륨) | `02_PREP_GUIDE.md` Part 2 | C2 배포 |
| P7 | 체크 행 단위 | **기능 그룹 + 체크 항목 행 [결정]** | 4-4, UI |
| P8 | 페르소나 세트 분리(`auto`/`manual`) + 백오피스 토큰 발급 | **반영** (필요 판단 근거 9-6) | 9-6, OpenAPI `Persona` |

---

## 13. 단계 계획 대응

| 단계 | 이 문서에서 사용하는 절 |
|---|---|
| C1 리뷰 확정 | 전체, 특히 12절 |
| C2 골격 | 2, 4, 6, 7, 8, 11 |
| C3 수집 어댑터 | 3, 5-2 #1~5, 10 |
| C4 QA 준비하기 | 5-2 #6~8, 5-3 |
| C5 admin-web | 7-2, 15, OpenAPI 초안 |
| C6 파일럿(수동) | 5-5, 5-7, 9-3 일부 |
| C7 자동화 | 5-4, 9, S1~S3 |
| C8 저장소 CI | 5-6 |

---

## 14. 사용자 투두 (설계 외부에서 처리할 일)

| # | 할 일 | 관련 |
|---|---|---|
| T1 | 3개 저장소(`gravit-server`·`gravit-web`·`gravit-aos`)에 `release` 브랜치 생성 요청 | P1 |
| T2 | `dev.gravit.inuappcenter.kr` 배포 브랜치를 `gravit-web` release로 맞추도록 요청 (T1 이후) | P4 |
| T3 | AOS 개발자에게 release마다 `versionCode` 증가 규칙 공지 | P2 |
| T4 | AOS QA 빌드(소셜 로그인 제거 + 토큰 주입)를 빌드 타입/flavor로 만들도록 요청 | 9-6 |
| T5 | `02_PREP_GUIDE.md` Part 2 인프라 값 기입 | P6 |
| T6 | #15 dev 테스트 API 접근 제한 처리 시점 결정 | 9-5 S5 |
| T7 | #16 admin-web `step-10:RAVVIT` 완료 처리, #17 D8 문서 정정(MCP → REST) | 01 문서 5절 |

---

## 15. 사용자 안내(가이드) 설계

### 15-1. 원칙

| 원칙 | 내용 |
|---|---|
| 작업을 가리지 않는다 | 모달 투어·팝업 금지. 안내는 화면 안의 고정 위치(상단 단계 표시, 다음 할 일 배너, 빈 상태, 인라인 힌트)에만 둔다 |
| 다음 할 일은 하나만 | 화면마다 "지금 할 일" 1개만 강조한다 |
| 막힌 이유는 버튼 옆에 | 진행할 수 없는 동작은 숨기지 않고, 이유와 남은 수를 함께 보여 준다 |
| 판단은 서버, 문구는 프론트 | 서버는 다음 할 일·막힌 이유를 **코드**로 내려 주고, 문구는 admin-web이 관리한다(문구 수정에 배포 1회) |
| 끌 수 있다 | 처음 쓰는 사람용 인라인 힌트는 닫을 수 있고, 닫은 상태는 브라우저에 기억한다 |

### 15-2. 화면별 안내 요소

| 화면 | 진행도 | 안내 |
|---|---|---|
| 스프린트 목록 | 행마다 현재 단계 칩 + 파트별(서버·웹·앱) 진행 막대 | 스프린트가 없을 때 빈 상태: "Notion 스프린트 페이지를 연결해 시작" |
| 스프린트 상단(공통) | 5단계 표시: 준비 → 분류 검토 → 체크 → 종료 → 완료 | "지금 할 일" 배너 1줄 + 이동 버튼 |
| QA 준비하기 | 작업 단계 8개(기준점·Notion·Figma·diff·OpenAPI·해석·검증·반영) 진행 표시 | 실패 단계에 사유와 재시도 버튼. 경고(예: versionCode 미증가, 명세 확인 필요)는 접힌 목록 |
| 분류 검토 | "검토 남은 항목 N개" | 항목마다 자동/수동 판정 근거(기준 5개) 펼쳐 보기. 확정 버튼 옆에 남은 수 |
| 체크리스트 | 기능 그룹별 완료율, 파트 필터("내 파트만 보기") | 미검수 셀로 바로 이동하는 "다음 미검수" 버튼. 셀 4상태 범례 상단 고정. 페르소나가 필요한 항목은 페르소나 이름과 토큰 발급 바로가기 |
| 리포트 | 자동 실행 요약, 미해결 FAIL 수 | 종료 가능 여부와 막힌 이유 |

### 15-3. 서버가 내려 주는 안내 정보

스프린트 응답에 `guide` 객체를 포함한다(OpenAPI `SprintGuide`).

| 필드 | 내용 |
|---|---|
| `stage`, `stageIndex`, `stageCount` | 5단계 중 현재 위치 |
| `nextAction` | 다음 할 일 코드 (`START_SETUP`, `WAIT_SETUP`, `RETRY_SETUP`, `REVIEW_ITEMS`, `CONFIRM_CHECKLIST`, `CHECK_CELLS`, `WAIT_AUTO_RUN`, `CLOSE_SPRINT`, `NONE`) |
| `blockers` | 막힌 이유 코드 + 남은 수 (`PROPOSED_ITEMS_REMAINING`, `UNCHECKED_CELLS_REMAINING`, `AUTO_RUN_IN_PROGRESS`, `SETUP_IN_PROGRESS`, `RELEASE_BRANCH_MISSING`) |

- 문구·화면 배치는 C5에서 admin-web 인테이크 문서(`QA_SPEC.md`)에 옮겨 확정한다.