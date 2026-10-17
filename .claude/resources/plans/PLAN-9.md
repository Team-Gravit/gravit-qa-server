# [PLAN-9] 스프린트 QA 준비와 체크리스트 진행

> 이슈: #9
> 브랜치: feat/9-sprint-setup-checklist

## 목표
Notion 스프린트를 등록하고, 셋업 작업(`SETUP`) 8단계로 Notion, Figma, 코드 diff, OpenAPI를 모아 LLM으로 체크리스트 초안을 만든 뒤, 관리자가 검토, 확정, 셀 체크, 종료까지 진행할 수 있게 한다.
근거: 설계 `03_DESIGN.md` 4, 5-1~5-3, 5-5, 5-7, 6, 10, 15절 / OpenAPI operationId `listSprints`, `createSprint`, `getSprint`, `closeSprint`, `startSetupJob`, `getChecklist`, `createChecklistItem`, `updateChecklistItem`, `confirmChecklist`, `updateCheckCell`, `listCheckCellHistory` / 가이드 3-1, 3-3. `docs/`는 현재 로컬본 기준(사용자 확인).

## 영향 범위
### 신규 파일
경로는 `src/main/java/gravit/qa/` 기준(리소스, 문서 제외).

**Flyway, 리소스**
- `src/main/resources/db/migration/V2__add_setup_result_columns.sql` — `sprint_feature.figma_node_ids`, `sprint_feature.warnings`, `sprint.warnings`, `sprint.prepare_failure_reason`
- `src/main/resources/prompts/checklist-draft-system.txt` — 체크 항목 초안 시스템 프롬프트(규칙, 기준 5개 정의, `{{personas}}`, `{{testCatalog}}`)
- `src/main/resources/prompts/checklist-draft-user.txt` — 기능 1개 입력 템플릿
- `src/main/resources/test-catalog.json` — 테스트 카탈로그, `[]`로 시작

**job**
- `job/dto/event/JobTerminatedEvent` — 작업 최종 종료(FAILED, CANCELLED) 사실. `jobId`, `type`, `sprintId`, `status`, `cause`(nullable)
- `job/dto/internal/ReapedJobDto` — 회수 결과 행(`id`, `type`, `sprintId`, `status`)

**sprint**
- `sprint/domain/Sprint` — 스프린트 Entity(`@Version`), 상태 전이 메서드
- `sprint/domain/SprintStatus`, `PrepareFailureReason`, `WarningCode`, `GuideStage`, `GuideAction`, `GuideBlockerCode` — enum
- `sprint/domain/Baseline`, `BaselineRepo`, `BaselineRepoId` — 기준점 Entity, 복합키
- `sprint/repository/SprintRepository`, `BaselineRepository`, `BaselineRepoRepository`
- `sprint/service/SprintQueryService`, `SprintCommandService`
- `sprint/facade/SprintFacade` — 목록, 상세, 등록, 종료, 분류 확정(스프린트·체크리스트·작업·실행 조합)
- `sprint/facade/SetupFacade` — 셋업 시작, APPLY 단계 반영(한 트랜잭션)
- `sprint/controller/SprintController`, `sprint/controller/docs/SprintControllerDocs`
- `sprint/handler/SetupJobHandler` — `SETUP` 처리기(8단계)
- `sprint/handler/SetupJobTerminatedListener` — `JobTerminatedEvent` → `PREPARE_FAILED`
- `sprint/support/SetupCollector` — 단계 1~5 수집과 스냅샷 파일 저장
- `sprint/support/SetupInterpreter` — 단계 6 기능별 LLM 호출, 분류, 실패 항목
- `sprint/support/SetupPromptBuilder` — 프롬프트 조립, 길이 상한 자르기
- `sprint/support/SprintGuideCalculator` — `guide` 계산(순수 함수)
- `sprint/config/SetupProps` — `qa.setup.max-prompt-chars`
- `sprint/dto/request/` — `SprintCreateRequest`, `SprintCloseRequest`
- `sprint/dto/response/` — `SprintSummaryResponse`, `SprintResponse`, `SprintGuideResponse`, `GuideBlockerResponse`, `BaselineResponse`, `BaselineRepoResponse`
- `sprint/dto/internal/` — `SprintDto`, `WarningDto`, `SprintGuideInputDto`, `SetupTargetDto`, `PreviousBaselineDto`, `SetupBaselineDto`, `SetupBaselineRepoDto`, `SetupNotionDto`, `SetupFeatureSpecDto`, `SetupFigmaDto`, `SetupDiffDto`, `SetupOpenApiDto`, `SetupSnapshotDto`, `SetupFeatureDraftDto`, `SetupResultDto`, `SetupPromptDto`

**feature**
- `feature/domain/Feature`, `SprintFeature`, `SprintFeatureId` — Entity, 복합키
- `feature/domain/Part`, `SpecStatus` — enum(Notion 라벨 보유)
- `feature/domain/PartListConverter` — `List<Part>` ↔ `String[]`(text[])
- `feature/repository/FeatureRepository`, `SprintFeatureRepository`
- `feature/service/FeatureQueryService`, `FeatureCommandService`
- `feature/dto/internal/` — `FeatureUpsertDto`, `FeatureDto`, `SprintFeatureDto`, `SprintFeatureCreateDto`
- `feature/dto/response/FeatureResponse`

**snapshot**
- `snapshot/domain/Snapshot`, `snapshot/repository/SnapshotRepository`, `snapshot/service/SnapshotCommandService`, `snapshot/dto/internal/SnapshotCreateDto`

**checklist**
- `checklist/domain/ChecklistItem`(`@Version`), `CheckCell`(`@Version`), `CheckCellId`, `CheckCellHistory`
- `checklist/domain/Classification`, `ReviewState`, `ItemOrigin`, `CellState`, `CellSource` — enum
- `checklist/repository/ChecklistItemRepository`, `CheckCellRepository`, `CheckCellHistoryRepository`
- `checklist/service/ChecklistQueryService`, `ChecklistCommandService`
- `checklist/service/port/TestCatalog` — 테스트 카탈로그 조회 포트
- `checklist/infrastructure/ClasspathTestCatalog` — `test-catalog.json` 구현
- `checklist/facade/ChecklistFacade` — 체크리스트 조회, 항목 추가/수정, 셀 체크(스프린트 상태 검증 조합)
- `checklist/controller/ChecklistController`(`/api/v1/sprints/{sprintId}`), `ChecklistItemController`(`/api/v1/checklist-items`), `controller/docs/` 2개
- `checklist/support/ChecklistClassifier` — 분류 규칙(코드 결정)
- `checklist/support/ChecklistDraftValidator` — LLM 출력 의미 검증, VALIDATE 단계 재확인
- `checklist/support/CheckCellEditPolicy` — 셀 수동 변경 가능 판정(`editable`, 422)
- `checklist/dto/request/` — `ChecklistItemCreateRequest`, `ChecklistItemUpdateRequest`, `ChecklistConfirmRequest`, `CheckCellUpdateRequest`
- `checklist/dto/response/` — `ChecklistResponse`, `ChecklistFeatureResponse`, `ChecklistItemResponse`, `CheckCellResponse`, `CheckCellHistoryResponse`, `ClassificationCriteriaResponse`, `CriterionResultResponse`, `ChecklistConfirmResponse`, `PartProgressResponse`
- `checklist/dto/internal/` — LLM 출력 `ChecklistDraftOutputDto`, `ChecklistDraftItemOutputDto`, `ClassificationCriteriaDto`, `CriterionResultDto` / `ChecklistItemDraftDto`, `ChecklistProposalDto`, `ClassificationResultDto`, `TestCatalogEntryDto`, 집계 프로젝션 `CellStateCountDto`, `SprintItemCountDto`

**run, persona** (C7 패키지를 C4 범위만큼 먼저 만든다)
- `run/domain/AutoRun`, `RunStatus`, `run/repository/AutoRunRepository`, `run/service/AutoRunCommandService`, `run/dto/response/AutoRunSummaryResponse` — 확정 시 `auto_run` 생성까지만
- `persona/domain/PersonaKey` — 설계 9-3 페르소나 13개(키, 상태 설명)

**adapter, global**
- `adapter/llm/support/LlmPromptTemplates` — `classpath:prompts/{name}.txt` 로드, `{{key}}` 치환
- `global/config/JacksonConfig` — `JsonNullableModule` 빈 등록(Spring Boot가 `Module` 빈을 `ObjectMapper`에 자동 등록, D2)

**정책 문서**
- `.claude/spec/qa-policy/setup-job.md` — 단계별 처리·실패 규칙, Notion 라벨 변환, 파트→플랫폼, 셀 생성, 분류, LLM 입력 범위·상한, 출력 타입 방식(record, `schemas/` 미사용), 스냅샷 경로
- `.claude/spec/qa-policy/checklist.md` — 편집 가능 상태, 셀 잠금, 확정·종료 조건, guide 표, 경고 저장 위치

### 수정 파일
- `global/exception/domain/CustomErrorCode` — `// Global`에 `VERSION_CONFLICT`, `// Sprint`, `// Checklist` 그룹 추가(아래 표)
- `global/exception/handler/GlobalExceptionHandler` — `OptimisticLockingFailureException` → 409 `VERSION_CONFLICT`(현재는 `DataAccessException` 처리기에 걸려 500)
- `job/repository/sql/JobQueueSql` — `REAP_STALE`의 `RETURNING`을 `id, type, sprint_id, status`로, `SELECT_LATEST_JOB_ID`, `SELECT_SPRINT_IDS_WITH_ACTIVE_JOB` 추가
- `job/repository/custom/JobQueueRepository`, `JobQueueRepositoryImpl` — `reapStale` 반환을 `List<ReapedJobDto>`로, `findLatestJobId`, `findSprintIdsWithActiveJob` 추가
- `job/support/JobRunner` — `ApplicationEventPublisher`, `TransactionTemplate` 주입. 실패·취소·회수의 상태 기록과 `JobTerminatedEvent` 발행을 한 트랜잭션으로. `reapStale()`은 기존처럼 ID 목록 반환
- `job/service/JobCommandService` — `requestCancel`이 대기 작업을 즉시 `CANCELLED`로 만들면 이벤트 발행
- `job/service/JobQueryService` — `findLatestJobId`, `hasActiveJob`, `findSprintIdsWithActiveJob` 추가
- `snapshot/infrastructure/SnapshotFileStore` — `save`에 `jobId` 인자 추가, 경로 `snapshots/{sprintId}/{jobId}/{source}/{fileName}`
- `adapter/llm/support/LlmOutputValidator` — `@Nullable` record 컴포넌트를 스키마에서 `[T, "null"]`로 허용(스키마와 `format` 문자열 모두)
- `src/main/resources/application.yml` — `qa.setup.max-prompt-chars: 30000`
- `build.gradle` — `org.openapitools:jackson-databind-nullable:0.2.6` 추가(D2)
- `.claude/spec/qa-policy/README.md` — 표에 `setup-job.md`, `checklist.md` 등록
- `.claude/rules/test-convention.md` — "실제 처리기가 있는 유형은 스텁 대신 `@MockitoSpyBean`으로 동작을 바꾼다" 추가
- `.claude/rules/project-structure.md` — `run/`, `persona/`를 C4에서 일부 만든다고 표기
- `src/test/java/gravit/qa/job/JobQueueIntegrationTest` — `StubSetupJobHandler` 제거, `@MockitoSpyBean SetupJobHandler`로 교체(실제 처리기와 유형 중복 시 `JobRunner` 기동 실패)
- `src/test/java/gravit/qa/snapshot/infrastructure/SnapshotFileStoreTest` — `jobId` 경로 반영

## [설계] 항목 결정
명세가 계획서에 구현 방식과 근거를 요구한 항목, 그리고 구현 중 확정한 설계다.

### 1. 스프린트 상태 동기화 - 리스너 방식
- `JobTerminatedEvent`를 job이 발행하고, `sprint/handler/SetupJobTerminatedListener`가 동기 `@EventListener`로 받는다.
- 발행 지점 4곳
  | 경로 | 위치 | 조건 |
  |---|---|---|
  | 재시도 소진, 재시도 불가 실패 | `JobRunner.fail` | `markFailed` 결과가 `FAILED` |
  | 실행 중 취소 | `JobRunner.execute`의 `JobCancelledException` | `markCancelled` 성공 |
  | heartbeat 회수 | `JobRunner.reapStale` | `REAP_STALE`이 `FAILED`/`CANCELLED`로 만든 행 |
  | 대기 중 취소 | `JobCommandService.requestCancel` | `QUEUED` → 즉시 `CANCELLED` |
- 작업 종료 기록과 스프린트 전이를 같은 트랜잭션에서 커밋한다. `JobRunner`는 `TransactionTemplate`으로 `markFailed`/`markCancelled`/`reapStale` + `publishEvent`를 감싸고, `requestCancel`은 기존 `@Transactional` 안에서 발행한다. 리스너가 동기라서 `JpaTransactionManager`의 같은 커넥션에서 스프린트가 갱신된다. 따라서 "작업은 FAILED인데 스프린트는 PREPARING"인 막다른 상태(재준비도 못 하는 상태)가 생기지 않는다.
- 리스너: `type == SETUP && sprintId != null`이면 `sprintCommandService.markPrepareFailed(sprintId, reason)`. 스프린트가 `PREPARING`일 때만 바꾼다(그 외는 무시). `reason`은 `cause instanceof ReleaseBranchNotFoundException`이면 `RELEASE_BRANCH_MISSING`, 아니면 null.
- 선택 근거
  1. 종료 경로 중 대기 중 취소는 API(`JobCommandService`)에서 일어나고, 여기에는 처리기 맵이 없다. 콜백 방식이면 `JobCommandService`도 처리기 목록을 알아야 한다.
  2. 회수(`REAP_STALE`)는 유형 필터 없이 모든 작업을 대상으로 하므로 e2e 컨테이너가 `SETUP`을 회수할 수 있다. 콜백은 "회수한 프로세스에 그 유형 처리기 빈이 있다"는 전제를 만든다. 리스너는 sprint 패키지에 항상 있다.
  3. job은 sprint를 모른 채 사실만 발행한다(역방향 의존 없음). C7의 `auto_run` 상태 동기화도 같은 이벤트에 리스너만 더하면 된다.
- 남는 위험: 이 트랜잭션 자체가 DB 오류로 롤백되면 작업이 `RUNNING`으로 남고 heartbeat가 계속 갱신된다. C2의 `markFailed` 실패 시와 같은 기존 위험이며 새로 생기지 않는다.

### 2. 기능별 Figma 노드, 경고 저장 - V2 마이그레이션
- 명세대로 `sprint_feature`에 `figma_node_ids TEXT[] NOT NULL DEFAULT '{}'`, `warnings JSONB NOT NULL DEFAULT '[]'`를 추가한다.
- 같은 V2에 두 컬럼을 더 넣는다(명세 밖이라 명시)
  - `sprint.warnings JSONB NOT NULL DEFAULT '[]'` — 스프린트 단위 경고(5번 항목)
  - `sprint.prepare_failure_reason VARCHAR(255)` + `CHECK (... IN ('RELEASE_BRANCH_MISSING'))` — guide의 `RELEASE_BRANCH_MISSING` 판정용. `job.last_error` 문자열 접두어(`ReleaseBranchNotFoundException:`)를 파싱하는 방식은 깨지기 쉽고, 목록 API에서 스프린트마다 작업을 조회해야 해서 택하지 않았다.
- 경고 JSON은 `[{"code": "FIGMA_FRAME_MISSING", "message": "AOS 프레임 없음"}]`이다. `ReportWarning`에서 `screenId`를 뺀 모양이며, 행이 기능 단위라 `screenId`는 필요 없다.
- `ChecklistFeature.warnings`는 코드 문자열만 중복 없이 내려준다(설계 15-1: 판단은 서버, 문구는 프론트). 메시지는 리포트(후속)에서 쓴다.

### 3. 입력 크기 상한
- diff는 파일 경로와 상태(`status`, `previous_filename`)만 넣고 patch 본문은 넣지 않는다.
- `SetupProps.maxPromptChars`(`qa.setup.max-prompt-chars`, 기본 30000자, `@Min(1000)`). 기준은 user 프롬프트이고, 시스템 프롬프트(규칙, 페르소나, 카탈로그)는 자르지 않는다.
- user 프롬프트 섹션 순서는 중요도 순이다: 기능 속성 → 명세 본문 → Figma 상태 → 스프린트 변경 요약 → OpenAPI 발췌 → 변경 파일. 상한을 넘으면 뒤에서 자르고 `(입력 길이 상한으로 이후 내용 생략)`을 붙인 뒤 기능 경고를 남긴다(코드는 D1). 그래서 변경 파일 목록이 가장 먼저 잘린다.
- 시작값 30000은 C6 파일럿에서 다시 정한다.

### 4. 테스트 카탈로그, 페르소나 목록 위치
- 테스트 카탈로그: 포트 `checklist/service/port/TestCatalog`(`boolean contains(String testRef)`, `List<TestCatalogEntryDto> findByScreenId(String screenId)`) + 구현 `checklist/infrastructure/ClasspathTestCatalog`(기동 시 `classpath:test-catalog.json` 1회 로드). 형식은 `[{"id": "LRN-02.empty-state", "screenId": "LRN-02", "title": "..."}]`이다. C4에서는 빈 배열이라 `AUTO`가 나오지 않는 것이 정상이다. 테스트에서는 `@Import`한 설정의 `@Primary` 구현으로 대체해 `AUTO` 경로를 검증한다. C7에서 e2e 빌드가 만든 카탈로그로 바꾼다.
- 페르소나: `persona/domain/PersonaKey` enum(`PRE_ONBOARDING("pre-onboarding", "온보딩 전 ...")` … `ADMIN`). 설정값이 아니라 상수로 둔다. 프롬프트에 키와 상태 설명을 함께 실어야 하고, 목록이 바뀌면 설계 9-3과 데이터셋(S2)도 함께 바뀌는 Git 작업이기 때문이다. `PersonaKey.isKnown(String key)`로 검증한다.

### 5. 종료 경고 저장 위치 - `sprint.warnings`
- 종료 때 Notion 기록이 실패하면 `sprint.warnings`에 `{"code": "NOTION_WRITE_FAILED", "message": ...}`를 추가한다.
- 리포트(후속)는 `sprint.warnings` + `sprint_feature.warnings` + `baseline_repo.version_code_warning`을 합쳐 `ReportWarning`을 만든다. 스프린트 단위 경고가 더 생겨도 컬럼을 늘리지 않는다.

### 6. 그 밖에 확정한 설계
- **스냅샷 기록 시점과 경로**: 파일은 단계 2~5에서 쓰고 `snapshot` 행은 APPLY 트랜잭션에서 넣는다(단계 1~7은 DB를 바꾸지 않음). 파일 경로에 `jobId`를 넣는다. 지금 경로(`snapshots/{sprintId}/{source}/{fileName}`)로는 재준비가 이전 작업의 파일을 덮어써, 유지되는 `CONFIRMED` 항목이 `source_refs`로 가리키는 스냅샷의 `content_hash`와 실제 파일이 어긋난다. 같은 작업을 재시도할 때는 같은 경로라 덮어써도 된다. 최종 실패한 작업의 파일은 행 없이 남는다.
- **LLM nullable 출력**: `BeanOutputConverter`(Spring AI 1.1.8)는 모든 속성을 required, 비널 타입으로 만든다. 스크래치에서 확인한 결과 `personaKey: null`은 "null 발견, string 예상", 생략은 "필수 속성 없음"으로 실패한다. 명세의 `personaKey`, `testRef`(nullable)를 지키도록 `LlmOutputValidator.createSchema`를 보강한다. record 컴포넌트의 접근자에 `org.springframework.lang.Nullable`이 있으면 그 속성의 `type`을 `[T, "null"]`로 바꾼다(중첩 record, 배열 `items`, `$defs` 참조까지 따라감). LLM이 보는 `format` 문자열 안의 스키마도 같은 것으로 교체한다. `type`이 없는 형태(`$ref`)에 `@Nullable`이 붙으면 `IllegalStateException`으로 알린다.
- **누락 처리(설계 5-2)**: `expected` 앞에 붙이는 "근거 없음" 표기는 LLM에 맡기지 않고 코드가 결정적으로 붙인다. 명세가 `NEEDS_CHECK`이면 `근거 없음(명세 확인 필요) - `, 항목 파트(WEB/APP)에 대응하는 플랫폼 프레임이 없으면 `근거 없음(Figma {WEB|AOS} 프레임 없음) - `. 경고는 `SPEC_NEEDS_CHECK`, `FIGMA_FRAME_MISSING`(플랫폼별)이다. 프롬프트에도 "주어진 근거 밖을 추측하지 말 것"을 넣는다.
- **`evidence` 출력 필드**: 저장하지 않는다(설계 테이블과 OpenAPI에 자리 없음). `@JsonPropertyOrder`로 판정 필드보다 앞에 두어 근거를 먼저 쓰게 하는 용도이고, 의미 검증은 비어 있지 않은지만 본다.
- **Notion 최신값 반영**: NOTION 단계에서 다시 읽은 `기간`, `Figma QA 페이지`로 APPLY에서 `period_*`, `figma_file_key`를 갱신하고, FIGMA 단계는 새로 읽은 URL의 키를 쓴다. 등록 후 Notion에서 Figma URL을 채워도 재등록 없이 준비할 수 있게 하려는 것이다. `sprint_key`는 등록 값으로 고정한다(UNIQUE, Figma Section 이름).
- **재준비 후 대상에서 빠진 기능**: `sprint_feature`는 새 대상으로 교체한다. 다만 그 기능에 `CONFIRMED`/`HUMAN` 항목이 남아 있으면 체크리스트 조회에 기능 그룹으로 계속 보인다(종료 조건에 잡히는 셀이 화면에서 사라지지 않게). `figmaNodeIds`, `warnings`는 빈 값이다.
- **실행 중 자동 실행 판정**: `job` 테이블의 활성(`QUEUED`/`RUNNING`) `AUTO_RUN` 작업으로 본다. `auto_run.status`는 C7 소비자 전까지 바뀌지 않으므로, 작업을 취소해도 종료가 영구히 막히지 않게 하려는 것이다.
- **재준비 실패**: `REVIEW`에서 다시 준비하다 실패하면 명세대로 `PREPARE_FAILED`가 된다. 기존 항목은 그대로 남지만 `REVIEW`로 돌아가려면 준비를 다시 성공시켜야 한다.

## 계약 차이 (OpenAPI 대비)
원칙은 OpenAPI 준수이며, 아래는 계약에 없는 응답이나 코드다.

| operation | 차이 | 이유 |
|---|---|---|
| `createSprint` | Notion 5xx 재시도 소진, 401/403 → **502** `NOTION_UNAVAILABLE` | 계약에 상류 장애 코드가 없다. 500으로 뭉치면 원인 구분이 안 된다 |
| `createSprint` | 스프린트 DB 행이 아니거나 URL에서 ID를 못 찾음, Figma URL 형식 오류 → 400 | 계약의 400(요청 형식 오류) 범위로 본다 |
| `createChecklistItem` | `AUTO` + 카탈로그에 없는 `testRef`, 모르는 `personaKey` → **422** | 계약의 생성에는 422가 없다. 수정(`updateChecklistItem`)과 같은 규칙으로 맞춘다 |
| 경고 코드 | `OPENAPI_OPERATION_MISSING`, `LLM_INPUT_TRUNCATED` 추가(D1) | `ChecklistFeature.warnings`는 string 배열이라 C4 응답은 계약 안이다. `ReportWarning.code` enum은 리포트 단계에서 확장해야 한다 |
| `updateChecklistItem` | 계약 그대로 구현: `testRef`/`personaKey`는 필드가 없으면 유지, null이면 해제(D2, `JsonNullable`) | 차이 아님. 구현 방식 기록 |

## 구현 계획

### 1. Entity / Flyway
**`V2__add_setup_result_columns.sql`** — [설계] 2의 4개 컬럼과 CHECK 제약. 문장마다 목적 주석을 단다.

**매핑 공통**
- 연관관계 매핑 없이 FK는 ID 컬럼(`Long sprintId`)으로 둔다(`open-in-view: false`, 지연 로딩 회피).
- `List<String>` 배열은 `@JdbcTypeCode(SqlTypes.ARRAY)`, `List<Part>`는 `@Convert(converter = PartListConverter.class)`(`String[]`), JSONB는 `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"`로 매핑한다. `ddl-auto: validate` 통과는 첫 통합 테스트에서 확인한다.
- 복합키는 `@IdClass`(`BaselineRepoId`, `SprintFeatureId`, `CheckCellId`).
- `updated_at`이 없는 테이블(`baseline`, `snapshot`, `check_cell_history`, `auto_run`)은 `BaseEntity`를 상속하지 않고 시각 필드를 직접 둔다.

| Entity | 팩토리, 메서드 |
|---|---|
| `Sprint` | `create(sprintKey, notionPageId, periodStart, periodEnd, figmaFileKey, createdBy)` → `DRAFT` / `startPreparing()`: DRAFT, PREPARE_FAILED, REVIEW만(그 외 `SPRINT_STATUS_CONFLICT`), 실패 사유 초기화 / `boolean failPreparation(PrepareFailureReason)`: PREPARING일 때만 바꾸고 true / `completePreparation(periodStart, periodEnd, figmaFileKey, now)`: PREPARING만(아니면 `IllegalStateException`), REVIEW, `prepared_at` / `confirm(version, now)`: REVIEW, version 일치(불일치 `VERSION_CONFLICT`) → IN_PROGRESS, `confirmed_at` / `close(version, now)`: IN_PROGRESS, version 일치 → CLOSED, `closed_at` / `addWarning(WarningDto)` |
| `Baseline` | `create(sprintId, previousBaselineId, now)` / `replace(previousBaselineId, now)`: 재준비 시 행(ID)은 유지하고 내용만 교체 |
| `BaselineRepo` | `create(baselineId, SetupBaselineRepoDto)`. 상수 `RELEASE_BRANCH = "release"` |
| `Feature` | `create(FeatureUpsertDto)` / `update(FeatureUpsertDto)` |
| `SprintFeature` | `create(sprintId, featureId, figmaNodeIds, warnings)` |
| `Snapshot` | `create(sprintId, jobId, SnapshotCreateDto)` |
| `ChecklistItem` | `propose(sprintId, featureId, ChecklistItemDraftDto, sourceRefs, sortOrder)` → LLM, PROPOSED / `createByHuman(sprintId, featureId, title, expected, parts, classification, testRef, personaKey, sortOrder)` → HUMAN, CONFIRMED, criteria null / `changeTitle`, `changeExpected`, `changeParts`, `changePersonaKey`, `changeTestRef`, `changeClassification`(AUTO면 `automationGap = false`), `review(ReviewState)`, `requireVersion(int)` |
| `CheckCell` | `static List<CheckCell> initialize(itemId, List<Part> itemParts)`: 3파트 모두, 포함 파트는 `UNCHECKED`, 나머지는 `NOT_APPLICABLE`, source `MANUAL` / `Optional<CheckCellHistory> applyParts(itemParts, adminId, now)`: 빠진 파트는 N/A, 다시 들어온 파트는 UNCHECKED, 바뀌면 이력 반환 / `CheckCellHistory check(CellState, adminId, now)` |
| `CheckCellHistory` | `of(itemId, part, fromState, toState, source, changedBy, autoRunId, changedAt)` |
| `AutoRun` | `create(sprintId, baselineId, jobId, now)` → QUEUED |

**enum**
- `SprintStatus`: DRAFT, PREPARING, PREPARE_FAILED, REVIEW, IN_PROGRESS, CLOSED
- `PrepareFailureReason`: RELEASE_BRANCH_MISSING
- `WarningCode`: VERSION_CODE_NOT_INCREASED, SPEC_NEEDS_CHECK, FIGMA_FRAME_MISSING, ITEM_GENERATION_FAILED, NOTION_WRITE_FAILED, OPENAPI_OPERATION_MISSING, LLM_INPUT_TRUNCATED (뒤의 2개는 D1)
- `Part(notionLabel)`: SERVER("서버"), WEB("웹"), APP("앱"). `static Optional<Part> fromNotionLabel(String)`
- `SpecStatus(notionLabel)`: DRAFT("역문서화 초안"), NEEDS_CHECK("확인 필요"), CONFIRMED("확정"). `fromNotionLabel`
- `Classification`, `ReviewState`, `ItemOrigin`(LLM, HUMAN), `CellState`, `CellSource`(MANUAL, AUTO), `RunStatus`
- `PersonaKey(key, description)` 13개 + `static boolean isKnown(String)`

### 2. Repository
- `SprintRepository`: `existsByNotionPageId(String)`, `existsBySprintKey(String)`, `findAllByOrderByCreatedAtDescIdDesc()`, `findAllByStatusOrderByCreatedAtDescIdDesc(SprintStatus)`
- `BaselineRepository`: `findBySprintId(long)` / `findClosedBefore(long sprintId, Limit limit)` — `SELECT b FROM Baseline b JOIN Sprint s ON s.id = b.sprintId WHERE s.status = :closed AND s.id <> :sprintId ORDER BY s.closedAt DESC, s.id DESC`
- `BaselineRepoRepository`: `findAllByBaselineIdOrderByRepo(long)`, `@Modifying deleteAllByBaselineId(long)`
- `FeatureRepository`: `findByNotionPageId(String)`, `findByScreenId(String)`, `findAllByIdIn(Collection<Long>)`
- `SprintFeatureRepository`: `findAllBySprintId(long)`, `existsBySprintIdAndFeatureId(long, long)`, `@Modifying deleteAllBySprintId(long)`
- `SnapshotRepository`: 기본 `saveAll`
- `ChecklistItemRepository`
  - `findAllBySprintIdOrderByFeatureIdAscSortOrderAscIdAsc(long)`
  - `@Modifying deleteProposed(long sprintId)` — `DELETE FROM ChecklistItem i WHERE i.sprintId = :sprintId AND i.reviewState = :proposed`(셀, 이력은 DB `ON DELETE CASCADE`)
  - `findMaxSortOrder(long sprintId, long featureId)` — `COALESCE(MAX(i.sortOrder), -1)`
  - `countBySprintIdAndReviewState(long, ReviewState)`, `existsBySprintIdAndClassificationAndReviewState(...)`
  - `countProposedBySprintIds(Collection<Long>, ReviewState)` → `List<SprintItemCountDto>`(`SELECT new … (i.sprintId, COUNT(i)) … GROUP BY i.sprintId`)
- `CheckCellRepository`
  - `findAllByItemIdIn(Collection<Long>)`, `findByItemIdAndPart(long, Part)`
  - `countStatesBySprintIds(Collection<Long> sprintIds, ReviewState rejected)` → `List<CellStateCountDto>` — `SELECT new …(i.sprintId, c.part, c.state, COUNT(c)) FROM CheckCell c JOIN ChecklistItem i ON i.id = c.itemId WHERE i.sprintId IN :sprintIds AND i.reviewState <> :rejected GROUP BY i.sprintId, c.part, c.state`
- `CheckCellHistoryRepository`: `findAllByItemIdAndPartOrderByChangedAtDescIdDesc(long, Part)`
- `AutoRunRepository`: 기본
- `JobQueueRepository`: `Optional<Long> findLatestJobId(long sprintId, JobType type)`, `Set<Long> findSprintIdsWithActiveJob(Collection<Long> sprintIds, JobType type)`, `reapStale` → `List<ReapedJobDto>`

목록 API의 진행률, 남은 PROPOSED 수, 자동 실행 여부는 스프린트 ID 묶음으로 한 번씩 집계한다(N+1 없음).

### 3. Service
**`SprintQueryService`** (`@Transactional(readOnly = true)`)
- `SprintDto getSprint(long sprintId)` — 없으면 `SPRINT_NOT_FOUND`
- `List<SprintDto> getSprints(SprintStatus status)` — null이면 전체, 최신 생성순
- `Optional<BaselineResponse> findBaseline(long sprintId)` — `compareUrl` = `https://github.com/{owner}/{repo}/compare/{prev}...{cur}`(prev 없으면 null, `GithubProps.owner`)
- `SetupTargetDto getSetupTarget(long sprintId)` — `sprintKey`, `notionPageId`, `status`
- `Optional<PreviousBaselineDto> findPreviousClosedBaseline(long sprintId)` — 직전 CLOSED 스프린트 기준점 ID, 저장소별 SHA, AOS `versionCode`
- `Long findBaselineId(long sprintId)`
- `String getNotionBaselineSummary(long sprintId)` — 저장소마다 `{repo} {branch} {commitSha}` 한 줄, `gravit-aos`에는 ` (versionCode {n}, versionName {v})`를 덧붙인다

**`SprintCommandService`** (`@Transactional`)
- `SprintDto create(NotionSprintDto notionSprint, String figmaFileKey, long adminId)` — `notionPageId` 또는 `sprintKey`가 있으면 `SPRINT_ALREADY_REGISTERED`. `saveAndFlush`의 `DataIntegrityViolationException`도 같은 코드
- `void startPreparing(long sprintId)`
- `void markPrepareFailed(long sprintId, PrepareFailureReason reason)` — 리스너 전용, PREPARING이 아니면 무시
- `void applyPreparation(long sprintId, SetupBaselineDto baseline, LocalDate periodStart, LocalDate periodEnd, String figmaFileKey, LocalDateTime now)` — `completePreparation`, 기준점 행 upsert, `baseline_repo` 삭제 후 재삽입
- `void confirm(long sprintId, int version, LocalDateTime now)`, `void close(long sprintId, int version, LocalDateTime now)`, `void addWarning(long sprintId, WarningDto warning)`

**`FeatureQueryService`**: `List<SprintFeatureDto> getSprintFeatures(long sprintId)`(화면 ID순), `List<FeatureDto> getFeatures(Collection<Long> featureIds)`, `boolean isSprintFeature(long sprintId, long featureId)`

**`FeatureCommandService`**
- `Map<String, Long> upsertAll(List<FeatureUpsertDto> features)` → `screenIdToFeatureId`. `notion_page_id`로 찾고, 없으면 `screen_id`로 찾아(Notion 페이지 재생성) `notion_page_id`를 갱신하고, 둘 다 없으면 새로 만든다
- `void replaceSprintFeatures(long sprintId, List<SprintFeatureCreateDto> sprintFeatures)`

**`SnapshotCommandService`**: `List<Long> saveAll(long sprintId, long jobId, List<SnapshotCreateDto> snapshots)` — 입력 순서대로 ID 반환

**`ChecklistQueryService`**
- `List<ChecklistItemResponse> getItems(long sprintId, SprintStatus sprintStatus)` — 항목 1회 + 셀 1회 조회, `editable`은 `CheckCellEditPolicy`
- `Map<Long, List<PartProgressResponse>> getProgress(Collection<Long> sprintIds)` → `sprintIdToProgress`. 파트 3개 항상 포함, `total`은 N/A 제외, REJECTED 항목 제외
- `Map<Long, Long> countProposed(Collection<Long> sprintIds)` → `sprintIdToProposedCount`
- `long countUnchecked(long sprintId)` — REJECTED 제외
- `boolean existsConfirmedAuto(long sprintId)`
- `long getSprintIdOfItem(long itemId)` — 없으면 `CHECKLIST_ITEM_NOT_FOUND`
- `List<CheckCellHistoryResponse> getCellHistory(long itemId, Part part)` — 셀 없으면 `CHECK_CELL_NOT_FOUND`, 최신순

**`ChecklistCommandService`**
- `void replaceProposed(long sprintId, List<ChecklistProposalDto> proposals)` — `deleteProposed` 후, 기능별 `findMaxSortOrder + 1`부터 항목을 넣고 셀 3개씩 생성
- `ChecklistItemResponse createItem(long sprintId, ChecklistItemCreateRequest request)` — 아래 검증 후 `createByHuman`, 셀 생성
- `ChecklistItemResponse updateItem(long itemId, ChecklistItemUpdateRequest request, long adminId)` — version 확인(409) → 전달된 필드만 변경 → `parts`가 바뀌면 셀마다 `applyParts`와 이력 저장 → 최종 상태 검증
- `CheckCellResponse updateCell(long itemId, Part part, CheckCellUpdateRequest request, long adminId)` — `NOT_APPLICABLE` 요청은 400 `CHECK_CELL_STATE_INVALID` → `CheckCellEditPolicy.requireEditable`(422) → version(409) → 같은 상태면 그대로 반환 → `check` + 이력 저장
- 공통 검증: `personaKey`는 null이거나 `PersonaKey.isKnown`(아니면 422 `PERSONA_KEY_UNKNOWN`). `testRef`는 null이거나 `TestCatalog.contains`(아니면 422 `TEST_REF_NOT_IN_CATALOG`). 최종 `classification == AUTO`이면 `testRef`가 있어야 한다(없으면 422 `TEST_REF_NOT_IN_CATALOG`). `reviewState = PROPOSED` 요청은 400 `INVALID_PARAMS`

**`AutoRunCommandService`**: `AutoRunSummaryResponse create(long sprintId, long baselineId, long jobId)`

**`JobQueryService`**: `Optional<Long> findLatestJobId(long sprintId, JobType type)`, `boolean hasActiveJob(long sprintId, JobType type)`, `Set<Long> findSprintIdsWithActiveJob(Collection<Long> sprintIds, JobType type)`

**`JobCommandService.requestCancel`**: `requestCancel` 결과가 `CANCELLED`(대기 작업이었음)이면 `findJob`으로 유형과 스프린트를 읽어 `JobTerminatedEvent` 발행(cause null)

**`JobRunner`**
- `fail(job, retryable, cause)`: `transactionTemplate.executeWithoutResult`로 `markFailed`를 실행하고, 결과가 `FAILED`면 이벤트 발행
- 취소: `markCancelled`가 true면 이벤트 발행(같은 트랜잭션)
- `reapStale()`: 트랜잭션 안에서 `reapStale` → `FAILED`/`CANCELLED` 행마다 이벤트 발행(cause null) → 기존처럼 ID 목록 반환
- `markSucceeded`는 이벤트 없음

### 4. Facade
**`SprintFacade`** (스프린트, 기능 없음 / 체크리스트 / 작업 / 실행 조합)
- `List<SprintSummaryResponse> listSprints(SprintStatus status)` — 스프린트 목록 → ID 묶음으로 진행률, PROPOSED 수, 활성 `AUTO_RUN` 집합 조회 → `SprintGuideCalculator`
- `SprintResponse getSprint(long sprintId)` — 위 정보 + 기준점 + `latestSetupJobId`
- `SprintResponse createSprint(SprintCreateRequest request, long adminId)` — 트랜잭션 밖에서 `notionClient.getSprint(url)` → 예외 매핑(404 → `NOTION_SPRINT_PAGE_NOT_FOUND`, status null인 재시도 불가 → `NOTION_SPRINT_PAGE_INVALID`, 그 외 외부 실패 → `NOTION_UNAVAILABLE`) → `figmaQaPageUrl`이 비어 있으면 키 null, 있으면 `FigmaFileKeys.fromUrl`(실패 400 `SPRINT_FIGMA_URL_INVALID`) → `sprintCommandService.create` → `getSprint`
- `SprintResponse closeSprint(long sprintId, SprintCloseRequest request)` — 트랜잭션(`TransactionTemplate`) 안에서 `countUnchecked > 0`이면 `SPRINT_UNCHECKED_CELLS_REMAIN`, `hasActiveJob(AUTO_RUN)`이면 `SPRINT_AUTO_RUN_IN_PROGRESS`, 아니면 `close` → 커밋 후 `notionClient.updateSprintBaseline(notionPageId, summary)` → `RuntimeException`이면 로그를 남기고 `addWarning(NOTION_WRITE_FAILED)`(별도 트랜잭션) → `getSprint`
- `ChecklistConfirmResponse confirmChecklist(long sprintId, ChecklistConfirmRequest request)` — 트랜잭션 안에서 `countProposed > 0`이면 `CHECKLIST_ITEMS_NOT_REVIEWED` → `confirm` → `existsConfirmedAuto && startAutoRun != false`이면 `jobCommandService.enqueue(AUTO_RUN, sprintId, Map.of())` + `autoRunCommandService.create(sprintId, baselineId, jobId)` → 커밋 후 `getSprint`와 함께 응답

**`SetupFacade`**
- `JobResponse startSetup(long sprintId)` — 트랜잭션 안에서 `startPreparing` + `enqueue(SETUP, sprintId, Map.of())`(실행 중이면 `JOB_ALREADY_ACTIVE`) → 커밋 후 `jobQueryService.getJob`
- `void apply(long sprintId, long jobId, SetupResultDto result)` — 한 트랜잭션: `applyPreparation` → `upsertAll` → `replaceSprintFeatures` → `snapshotCommandService.saveAll` → 기능별 `source_refs`(NOTION_SPRINT, 해당 NOTION_SPEC, 해당 FIGMA_FRAME, OPENAPI, GITHUB_DIFF 스냅샷 ID) 조립 → `replaceProposed`

**`ChecklistFacade`** (스프린트 상태 + 기능 + 체크리스트)
- `ChecklistResponse getChecklist(long sprintId)` — 스프린트 상태 → 대상 기능 + 항목이 있는 그 밖의 기능 → 항목을 기능별로 묶음
- `ChecklistItemResponse createItem(long sprintId, ChecklistItemCreateRequest request)` — REVIEW가 아니면 `SPRINT_STATUS_CONFLICT`, 대상 기능이 아니면 `CHECKLIST_FEATURE_NOT_IN_SPRINT`(404)
- `ChecklistItemResponse updateItem(long itemId, ChecklistItemUpdateRequest request, long adminId)` — 항목의 스프린트가 REVIEW가 아니면 409
- `CheckCellResponse updateCell(long itemId, Part part, CheckCellUpdateRequest request, long adminId)` — 항목의 스프린트가 IN_PROGRESS가 아니면 409

상태 검증은 사전 검증 조회로 트랜잭션 밖에 두고, 쓰기는 서비스 1회 호출이라 Facade에 경계를 만들지 않는다(facade.md).

### 5. 셋업 작업 (Handler / Support)
**`SetupJobHandler.handle(JobContext context)`**
```
target   = sprintQueryService.getSetupTarget(sprintId)        // PREPARING이 아니면 NonRetryableJobException
baseline = step("BASELINE", collector.collectBaseline(sprintId))
notion   = step("NOTION",   collector.collectNotion(sprintId, jobId, target.notionPageId()))
figma    = step("FIGMA",    collector.collectFigma(sprintId, jobId, notion.figmaFileKey(), target.sprintKey(), notion.specs()))
diff     = baseline.previousBaselineId() == null
             ? skipStep("DIFF", "직전 기준점 없음 - 전체 범위")
             : step("DIFF", collector.collectDiff(sprintId, jobId, baseline))
openApi  = step("OPENAPI",  collector.collectOpenApi(sprintId, jobId, notion.specs()))
features = step("INTERPRET", interpreter.interpret(notion, figma, diff, openApi, context))   // 기능 사이마다 checkCancelled
           step("VALIDATE",  checklistDraftValidator.verify(features))                         // 실패 시 NonRetryableJobException
           step("APPLY",     setupFacade.apply(sprintId, jobId, result))
```

**`SetupCollector`**
| 단계 | 메서드 | 처리 | 실패 |
|---|---|---|---|
| BASELINE | `SetupBaselineDto collectBaseline(long sprintId)` | `GithubRepo` 3개 `getBranchHeadSha(repo, "release")` → `findPreviousClosedBaseline`의 SHA를 `previousCommitSha`로 → `gravit-aos`는 `getFileContent(AOS, BUILD_GRADLE_PATH, sha)` + `AosVersionParser.parse(content, previousVersionCode)` | `ReleaseBranchNotFoundException`은 그대로 던진다(재시도 불가, 리스너가 사유 기록) |
| NOTION | `SetupNotionDto collectNotion(sprintId, jobId, notionPageId)` | `getSprint` → `NOTION_SPRINT` 스냅샷(`{pageId}.json`) → 대상 기능마다 `getSpec` + `getPageBody` → `NOTION_SPEC` 스냅샷(`{pageId}.json` = `{page, markdown, blocks}`) → 라벨 변환 → `FigmaFileKeys.fromUrl(figmaQaPageUrl)` | 대상 기능 0개, 파트 비어 있음, 모르는 파트·명세 상태 라벨(원문을 메시지에), 화면 ID 중복 → `NonRetryableJobException`. Figma URL이 없으면 키 null(FIGMA에서 실패) |
| FIGMA | `SetupFigmaDto collectFigma(sprintId, jobId, fileKey, sprintKey, specs)` | 키 null이면 실패 → `getQaFrames` → 기능 파트를 플랫폼으로(`WEB→WEB`, `APP→AOS`, `SERVER→없음`, 상수 Map) → `findScreen`의 프레임 node ID 수집, 없으면 `FIGMA_FRAME_MISSING`(메시지에 플랫폼, QA 페이지·Section 없음이면 그 사유) → `exportPng` → `FIGMA_FRAME` 스냅샷 `{플랫폼}_{nodeId에서 ':'→'-'}.png`, `source_ref` = nodeId. 렌더 실패한 노드는 로그만 남긴다 | 키 없음 → `NonRetryableJobException` |
| DIFF | `SetupDiffDto collectDiff(sprintId, jobId, baseline)` | 저장소별 `compare(prev, cur)`(같은 SHA면 호출 없이 빈 목록) → `GITHUB_DIFF` 스냅샷 `{repo}.json` = `[{filename, status, additions, deletions, previousFilename}]`, `source_ref` = `repo@prev..cur` | - |
| OPENAPI | `SetupOpenApiDto collectOpenApi(sprintId, jobId, specs)` | `fetchApiDocs` 1회 → `OPENAPI` 스냅샷 `api-docs.json`(원문), `source_ref` = URL → 기능별 `extract(relatedOperationIds)` → `missingOperationIds`가 있으면 기능 경고(D1) | - |

명세 상태가 `NEEDS_CHECK`이면 NOTION 단계에서 `SPEC_NEEDS_CHECK` 경고를 남긴다.

**`SetupInterpreter.interpret(...)`** — 기능마다
1. `SetupPromptBuilder.build(...)` → `SetupPromptDto(LlmPromptDto prompt, boolean truncated)`. 잘리면 기능 경고(D1)
2. `llmClient.generate(prompt, ChecklistDraftOutputDto.class, output -> checklistDraftValidator.validate(output, featureParts))`
3. 항목마다 `ChecklistClassifier.classify(criteria, testRef)` → `ChecklistItemDraftDto`. "근거 없음" 접두는 [설계] 6
4. `LlmOutputInvalidException` → 그 기능만 항목 1개: 제목 `초안 생성 실패`, `expected` = 실패 안내 + 오류 요약, `parts` = 기능 파트, MANUAL, criteria null, 경고 `ITEM_GENERATION_FAILED`. 다음 기능으로 계속한다
5. `LlmRateLimitedException` 등 그 밖의 예외는 그대로 던진다(재시도 불가 / 재시도 대상은 기존 분류 그대로)

**`SetupPromptBuilder`** — `LlmPromptTemplates.render("checklist-draft-system", {personas, testCatalog})`, `render("checklist-draft-user", {screenId, name, route, parts, specStatus, specMarkdown, figmaStates, changeSummary, openApiExcerpt, changedFiles})`. Figma 상태는 플랫폼별 `FigmaScreenDto.states()`와 프레임 수(이미지 아님), 카탈로그는 `findByScreenId(screenId)`만, 변경 파일은 저장소별 `{status} {filename}`. 상한 처리는 [설계] 3

**LLM 출력 타입** (`checklist/dto/internal`, 파일 하나에 record 하나, dto.md의 LLM 규칙)
- `ChecklistDraftOutputDto(List<ChecklistDraftItemOutputDto> items)`
- `ChecklistDraftItemOutputDto(evidence, title, expected, List<Part> parts, ClassificationCriteriaDto criteria, @Nullable personaKey, @Nullable testRef)` — `@JsonPropertyOrder`로 `evidence`가 맨 앞, `@JsonPropertyDescription`에 판정 기준
- `ClassificationCriteriaDto(specDecidable, preconditionReproducible, observable, noExternalDependency, notSubjective)` — 각각 `CriterionResultDto`. `checklist_item.criteria` JSON에도 그대로 쓴다
- `CriterionResultDto(reason, met)` — `reason`이 먼저

**`ChecklistDraftValidator`**
- `List<String> validate(ChecklistDraftOutputDto output, List<Part> featureParts)` — 항목 1개 이상, `title`/`expected`/`evidence` 공백 아님, `parts` 1개 이상, 중복 없음, 기능 파트의 부분집합, `personaKey`는 null 또는 `PersonaKey.isKnown`, `testRef`는 null 또는 `TestCatalog.contains`
- `void verify(List<SetupFeatureDraftDto> features)` — 같은 규칙에 "AUTO면 testRef가 카탈로그에 있음"을 더해 재확인

**`ChecklistClassifier.classify(ClassificationCriteriaDto criteria, String testRef)`** → `ClassificationResultDto(classification, automationGap)`: 5개 모두 met이고 `testRef`가 카탈로그에 있으면 `AUTO`, 5개 모두 met이고 그 외면 `MANUAL` + gap true, 하나라도 미충족이면 `MANUAL` + gap false

**`SprintGuideCalculator.calculate(SprintGuideInputDto input)`** → `SprintGuideResponse`. 입력: 상태, 실패 사유, PROPOSED 수, 파트별 UNCHECKED 수, 자동 실행 활성 여부. 명세 6절 표 그대로이고 `stageCount = 5`, `stageIndex`는 PREPARE=1 … DONE=5다. IN_PROGRESS에서 자동 실행이 활성이면 `WAIT_AUTO_RUN`(blocker `AUTO_RUN_IN_PROGRESS` + 남은 파트별 `UNCHECKED_CELLS_REMAINING`), UNCHECKED가 있으면 `CHECK_CELLS`(파트별 blocker, count > 0인 파트만), 둘 다 없으면 `CLOSE_SPRINT`(stage CLOSE, blocker 없음)

**`CheckCellEditPolicy`** — `boolean isEditable(SprintStatus, Classification, ReviewState, Part, CellState)` = IN_PROGRESS && REJECTED 아님 && N/A 아님 && !(AUTO && part ∈ {WEB, SERVER}). `requireEditable(...)`는 불가 시 422 `CHECK_CELL_LOCKED`(스프린트 상태 불일치 409는 Facade가 먼저 거른다)

**`LlmPromptTemplates.render(String name, Map<String, String> values)`** — 처음 읽을 때 캐시하고, 남은 `{{…}}`가 있으면 `IllegalStateException`

**`ClasspathTestCatalog`** — `@PostConstruct`에서 `ObjectMapper`로 `List<TestCatalogEntryDto>` 로드, `id` 중복이면 기동 실패

### 6. DTO
요청 (`@Schema` + validation)
- `SprintCreateRequest(@NotBlank String notionPageUrl)`
- `SprintCloseRequest(@NotNull Integer version)`
- `ChecklistItemCreateRequest(@NotNull Long featureId, @NotBlank title, @NotBlank expected, @NotEmpty Set<Part> parts, @NotNull Classification classification, String testRef, String personaKey)`
- `ChecklistItemUpdateRequest(@Size(min = 1) title, @Size(min = 1) expected, @Size(min = 1) Set<Part> parts, Classification classification, JsonNullable<String> testRef, JsonNullable<String> personaKey, ReviewState reviewState, @NotNull Integer version)` — `JsonNullable` 필드는 `@Schema(implementation = String.class, nullable = true)`로 Swagger 표기를 맞춘다. 필드가 없으면 Jackson이 null을 넣을 수 있으므로 서비스는 `null` 또는 `isPresent() == false`를 "유지"로 본다
- `ChecklistConfirmRequest(Boolean startAutoRun, @NotNull Integer version)` — null이면 true
- `CheckCellUpdateRequest(@NotNull CellState state, @NotNull Integer version)`

응답 (OpenAPI 스키마와 같은 필드, 시각은 `KstTimes.toOffset`)
- `SprintSummaryResponse(guide, id, sprintKey, periodStart, periodEnd, status, progress)`
- `SprintResponse` — Summary 필드 + `notionPageId, figmaFileKey, baseline, latestSetupJobId, preparedAt, confirmedAt, closedAt, createdAt, createdBy, version`
- `SprintGuideResponse(stage, stageIndex, stageCount, nextAction, blockers)`, `GuideBlockerResponse(code, count, part)` — stage, nextAction, code는 enum(`sprint/domain`에 `GuideStage`, `GuideAction`, `GuideBlockerCode`로 둔다)
- `BaselineResponse(id, previousBaselineId, createdAt, repos)`, `BaselineRepoResponse(repo, branch, commitSha, previousCommitSha, compareUrl, aosVersionCode, aosVersionName, versionCodeWarning)`
- `PartProgressResponse(part, total, pass, fail, unchecked, notApplicable)`
- `ChecklistResponse(sprintId, sprintStatus, features)`, `ChecklistFeatureResponse(feature, warnings, items)`, `FeatureResponse(id, screenId, name, route, parts, specStatus, notionUrl, figmaNodeIds)` — `notionUrl` = `https://www.notion.so/{pageId}`
- `ChecklistItemResponse(id, featureId, title, expected, parts, classification, criteria, automationGap, testRef, personaKey, sourceRefs, origin, reviewState, cells, version)`, `ClassificationCriteriaResponse`, `CriterionResultResponse`
- `CheckCellResponse(itemId, part, state, source, autoRunId, editable, updatedBy, updatedAt, version)`, `CheckCellHistoryResponse(fromState, toState, source, changedBy, autoRunId, changedAt)`
- `ChecklistConfirmResponse(sprint, autoRun)`, `AutoRunSummaryResponse(id, sprintId, jobId, status, counts, failureReason, createdAt, startedAt, finishedAt)`

**에러 코드** (`CustomErrorCode`)
| 그룹 | 코드 |
|---|---|
| Global | `VERSION_CONFLICT(CONFLICT, "GLOBAL_4091", "다른 사용자가 먼저 변경했습니다. 다시 불러온 뒤 시도하세요.")` |
| Sprint | `NOTION_SPRINT_PAGE_INVALID(BAD_REQUEST, "SPRINT_4001")`, `SPRINT_FIGMA_URL_INVALID(BAD_REQUEST, "SPRINT_4002")`, `SPRINT_NOT_FOUND(NOT_FOUND, "SPRINT_4041")`, `NOTION_SPRINT_PAGE_NOT_FOUND(NOT_FOUND, "SPRINT_4042")`, `SPRINT_ALREADY_REGISTERED(CONFLICT, "SPRINT_4091")`, `SPRINT_STATUS_CONFLICT(CONFLICT, "SPRINT_4092")`, `SPRINT_UNCHECKED_CELLS_REMAIN(CONFLICT, "SPRINT_4093")`, `SPRINT_AUTO_RUN_IN_PROGRESS(CONFLICT, "SPRINT_4094")`, `NOTION_UNAVAILABLE(BAD_GATEWAY, "SPRINT_5021")` |
| Checklist | `CHECK_CELL_STATE_INVALID(BAD_REQUEST, "CHECKLIST_4001")`, `CHECKLIST_ITEM_NOT_FOUND(NOT_FOUND, "CHECKLIST_4041")`, `CHECKLIST_FEATURE_NOT_IN_SPRINT(NOT_FOUND, "CHECKLIST_4042")`, `CHECK_CELL_NOT_FOUND(NOT_FOUND, "CHECKLIST_4043")`, `CHECKLIST_ITEMS_NOT_REVIEWED(CONFLICT, "CHECKLIST_4091")`, `TEST_REF_NOT_IN_CATALOG(UNPROCESSABLE_ENTITY, "CHECKLIST_4221")`, `PERSONA_KEY_UNKNOWN(UNPROCESSABLE_ENTITY, "CHECKLIST_4222")`, `CHECK_CELL_LOCKED(UNPROCESSABLE_ENTITY, "CHECKLIST_4223")` |

메시지는 구현 시 한글 문장으로 채운다.

### 7. Controller
**`SprintController`** (`/api/v1/sprints`, `SprintFacade`, `SetupFacade`)
- `GET ""` → `listSprints(@RequestParam(required = false) SprintStatus status)` → 200
- `POST ""` → `createSprint(@Valid SprintCreateRequest, @AuthenticationPrincipal LoginAdmin)` → 201
- `GET /{sprintId}` → `getSprint` → 200
- `POST /{sprintId}/close` → `closeSprint(@Valid SprintCloseRequest)` → 200
- `POST /{sprintId}/setup-jobs` → `startSetupJob` → 202 + `JobResponse`

**`ChecklistController`** (`/api/v1/sprints/{sprintId}`, `ChecklistFacade`, `SprintFacade`)
- `GET /checklist` → 200
- `POST /checklist-items` → `createChecklistItem(@Valid ChecklistItemCreateRequest)` → 201
- `POST /checklist/confirm` → `confirmChecklist(@Valid ChecklistConfirmRequest)` → 200

**`ChecklistItemController`** (`/api/v1/checklist-items`, `ChecklistFacade`, `ChecklistQueryService`)
- `PATCH /{itemId}` → `updateChecklistItem(@Valid ChecklistItemUpdateRequest, LoginAdmin)` → 200
- `PUT /{itemId}/cells/{part}` → `updateCheckCell(@PathVariable Part part, @Valid CheckCellUpdateRequest, LoginAdmin)` → 200
- `GET /{itemId}/cells/{part}/history` → `listCheckCellHistory` → 200

Docs 3개는 `JobControllerDocs` 형식(✅/🚨, 실제 코드·메시지 예시, `GLOBAL_5001`)을 따른다(`write-api-docs` 규칙).

### 8. 정책 문서, 규칙
- `setup-job.md`: 단계표(처리, 스냅샷, 실패 규칙), 라벨 변환표, 파트→플랫폼, 셀 생성, 분류, 누락 처리 접두, LLM 입력 범위와 상한, 출력 타입은 record + `LlmOutputValidator.schemaOf`(설계 11절 `schemas/` 대신, `@Nullable` 처리 포함), 스냅샷 경로와 기록 시점, 상태 동기화 이벤트
- `checklist.md`: 상태별 편집 가능 동작(REVIEW: 항목, IN_PROGRESS: 셀), 셀 잠금 규칙, 확정·종료 조건, 실행 중 자동 실행 판정, guide 표, 경고 저장 위치(`sprint.warnings`, `sprint_feature.warnings`, `baseline_repo`), 재준비 시 보존 규칙
- `README.md` 표 등록, `test-convention.md`, `project-structure.md` 갱신

## 결정 필요 (Decisions needed)
- [x] D1. OpenAPI operationId 미발견, 프롬프트 잘림의 기능 경고 코드 — **A 확정**: `OPENAPI_OPERATION_MISSING`, `LLM_INPUT_TRUNCATED`를 `WarningCode`에 추가(C4 응답은 계약 안, 리포트 단계에서 `ReportWarning` enum 확장 필요) / B. 코드 없이 로그만 남김
- [x] D2. `updateChecklistItem`의 `testRef`, `personaKey`에서 "필드 없음(유지)"과 "null(해제)" 구분 (Jackson `Optional`은 둘 다 `empty`라 구분 불가, 스크래치 확인) — **A 확정**: `org.openapitools:jackson-databind-nullable:0.2.6` 추가, `JsonNullable<String>`(없음 = `undefined` → 유지, null → 해제, 값 → 변경) / B. `JsonNode` 필드 / C. 해제 미지원

## 검증
- 대상 테스트: 아래 전부, `./gradlew build` 통과

**단위 (순수 JUnit)**
- `checklist/support/ChecklistClassifierTest` — 5개 충족 + 카탈로그 있음 → AUTO / 5개 충족 + testRef 없음 → MANUAL, gap / 하나 미충족 → MANUAL, gap 없음 / testRef 있으나 미충족 → MANUAL
- `feature/domain/PartTest`, `SpecStatusTest` — 라벨 3종씩 변환, 모르는 라벨, null
- `checklist/domain/CheckCellTest` — `initialize`가 3셀(포함 UNCHECKED, 미포함 N/A), `applyParts`로 빠짐 → N/A, 재포함 → UNCHECKED, 변화 없으면 이력 없음
- `sprint/support/SprintGuideCalculatorTest` — `@ParameterizedTest`로 6절 표 전 행(PREPARE_FAILED 사유 유무, REVIEW 남은 수 유무, IN_PROGRESS 자동 실행·미검수 조합 4가지, stageIndex)
- `checklist/support/ChecklistDraftValidatorTest` — 항목 0개, 파트 범위 밖, 모르는 personaKey, 카탈로그 밖 testRef, 빈 문자열, 정상
- `adapter/llm/support/LlmOutputValidatorTest` — `@Nullable` 필드 null 허용, 비널 필드 null 거부, 중첩 record 안의 `@Nullable`
- `sprint/support/SetupPromptBuilderTest` — 상한 이하 그대로, 초과 시 뒤에서 잘림 + `truncated`

**통합 (`@QaSpringBootTest`, 외부는 `HttpStubServer`)**
- `sprint/handler/SetupJobHandlerIntegrationTest`
  - 성공 → REVIEW, `prepared_at`, 기준점 3개, `sprint_feature.figma_node_ids`, 스냅샷 행, 항목·셀(파트 ↔ N/A), 단계 7개 DONE + DIFF SKIPPED
  - 직전 CLOSED 기준점이 있으면 DIFF 실행, `previous_commit_sha`, versionCode 미증가 경고
  - release 없음 → 작업 FAILED(재시도 없음), 스프린트 PREPARE_FAILED + guide `RELEASE_BRANCH_MISSING`
  - LLM 출력 3회 실패 → 해당 기능만 "초안 생성 실패" 1개 + `ITEM_GENERATION_FAILED`, 다른 기능은 정상
  - LLM 429 연속 → FAILED, PREPARE_FAILED
  - 대상 기능 0개 / 모르는 Notion 라벨 / Figma URL 없음 → 재시도 없이 FAILED
  - NEEDS_CHECK 명세, 프레임 없는 기능 → `expected` 접두와 경고
  - 재준비(REVIEW) → PROPOSED만 교체, CONFIRMED·HUMAN 항목 유지
  - 테스트 카탈로그 대체 → AUTO 분류
  - 실행 중 취소, 대기 중 API 취소, heartbeat 회수로 시도 소진 → 모두 PREPARE_FAILED
- `sprint/controller/SprintControllerIntegrationTest` (MockMvc)
  - 등록 성공(DRAFT, 기간 null, `created_by`), 같은 페이지·같은 `sprint_key` 409, Notion 404 → 404, Notion 5xx → 502
  - 목록 상태 필터, `guide`·`progress`
  - `setup-jobs` 202 + PREPARING, 불가 상태 409, 실행 중 작업 409
  - 종료: 미검수 남음 409, 자동 실행 중 409, version 불일치 409, Notion 실패해도 CLOSED + `NOTION_WRITE_FAILED`, 성공 시 PATCH 본문의 기준점 요약
- `checklist/controller/ChecklistControllerIntegrationTest` (MockMvc)
  - 조회: 기능 그룹, 경고 코드, `editable`
  - 항목 추가 HUMAN·CONFIRMED, REVIEW 아니면 409, 대상 기능 아님 404
  - 수정: parts 변경 시 셀 전환과 이력, AUTO 전환 시 카탈로그 없는 testRef 422, version 불일치 409, D2 해제 동작
  - 확정: PROPOSED 남음 409, 성공 IN_PROGRESS, AUTO + `startAutoRun` → `auto_run`·`AUTO_RUN` 작업, false면 없음
  - 셀 체크: 이력 기록, 낙관적 잠금 409, AUTO 항목 WEB 셀 422, N/A 셀 422, REJECTED 항목 422, IN_PROGRESS 아님 409, NOT_APPLICABLE 요청 400
  - 이력 최신순
- 수정: `JobQueueIntegrationTest`(SETUP 스파이로 교체 후 기존 시나리오 유지), `SnapshotFileStoreTest`(jobId 경로)

**fixture**
- `sprint/fixture/SetupStubFixture` — Notion, Figma, GitHub, OpenAPI, LLM 스텁 응답 등록
- `checklist/fixture/TestCatalogStubConfig` — `@Primary` 카탈로그 대체(`@Import`)
- `sprint/fixture/SprintFixture` — 저장형(`@TestComponent`)

**실데이터 미검증(남는 위험)**
- 3개 저장소에 `release` 브랜치가 없어 실제 셋업은 BASELINE에서 실패하는 것이 정상이다(T1). 2~8단계는 스텁으로만 검증된다.
- 실제 LLM이 nullable 스키마와 프롬프트로 의미 검증을 몇 회 안에 통과하는지, 30000자 상한이 적절한지는 C6 파일럿에서 본다.
- GitHub compare는 파일 300개까지만 돌려준다(그 이상은 목록이 잘림).

## Deviation Log
> implement 스킬이 구현 중 계획을 벗어난 지점을 여기에 기록한다. (작성 시점엔 비워둔다)

- `feature/domain/PartListConverter`: 만들지 않음. `Feature.parts`, `ChecklistItem.parts`는 `List<String>` + `@JdbcTypeCode(SqlTypes.ARRAY)`로 두고 `getParts()`와 변경 메서드에서 `Part`로 바꾼다(`Part.toNames`, `Part.fromNames`) — 이유: Hibernate 6.6이 `AttributeConverter<List<Part>, String[]>`를 배열 컬럼에 쓰면 `CustomMutabilityConvertedBasicTypeImpl cannot be cast to BasicPluralType`로 SessionFactory 생성에 실패함(기존 테스트 70건 실패로 확인)
- `checklist/domain/CheckCell`: `@Version`을 `int` 대신 `Integer`로 — 이유: 복합키를 직접 넣는 엔티티라 Spring Data가 `version == null`로만 새 엔티티를 판별한다. `int`면 `merge`가 되어 Hibernate 6.6에서 행이 없는 버전 엔티티 병합이 낙관적 잠금 예외로 바뀔 수 있음
- `checklist/support/ChecklistDraftValidator`: `verify(List<SetupFeatureDraftDto>)` 대신 `validateDrafts(List<ChecklistItemDraftDto>, List<Part>)`를 두고, VALIDATE 단계의 기능별 반복과 `NonRetryableJobException`은 `SetupJobHandler.validate`가 맡음 — 이유: checklist 지원 클래스가 sprint 내부 DTO에 의존하지 않게
- `checklist/repository/ChecklistItemRepository`: `countProposedBySprintIds` 대신 `countBySprintIdsAndReviewState(sprintIds, reviewState)` — 이유: 검토 상태를 인자로 받는 일반형이 같은 쿼리로 충분함
- `checklist/repository/CheckCellRepository`: `countBySprintIdAndState(sprintId, rejected, state)`, `findAllByItemId(itemId)` 추가 — 이유: `countUnchecked`(종료 조건)와 항목 수정 시 셀 전환에 필요
- `sprint/repository/BaselineRepository.findClosedBefore`: `SprintStatus closed` 인자 추가 — 이유: JPQL에 enum 리터럴 대신 파라미터로 넘김
- `checklist/service/ChecklistQueryService`: `countProposed(long sprintId)` 단건 오버로드 추가 — 이유: 상세 조회와 확정에서 스프린트 1개만 셈
- `sprint/service/SprintQueryService.findBaselineId`: 반환형 `long`, 기준점이 없으면 `IllegalStateException` — 이유: REVIEW 이후에는 항상 기준점이 있음(없으면 불변식 위반)
- `sprint/dto/internal/SetupDiffDto`: 저장소별 목록 DTO 대신 `Map<String, List<GithubChangedFileDto>> repoToFiles` — 이유: 별도 DTO 없이 저장소 순서를 유지한 맵으로 충분함
- `checklist/dto/request/ChecklistConfirmRequest`: `shouldStartAutoRun()` 추가(null이면 true) — 이유: 기본값 판정을 Facade에 흩지 않게
- `run/dto/response/AutoRunSummaryResponse.counts`: `@JsonRawValue String` — 이유: `auto_run.counts`(JSONB)를 C7 전에 별도 타입 없이 JSON 그대로 내보냄(C4에서는 항상 null)
- `sprint/support/SetupCollector.PART_TO_PLATFORM`: public 상수로 두고 `SetupInterpreter`가 "근거 없음" 접두의 플랫폼 이름에 재사용 — 이유: 파트→플랫폼 규칙을 한 곳에 둠
