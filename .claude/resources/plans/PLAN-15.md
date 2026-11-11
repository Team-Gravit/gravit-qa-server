# [PLAN-15] 바뀐 기능 명세 구조로 멈춘 셋업 복구

> 이슈: #15
> 브랜치: fix/15-notion-spec-table

## 목표
Notion 기능 명세 DB의 `파트`, `라우트`, `관련 API`, `Figma`, `명세 상태`, `FE 스펙 ID`가 본문 첫 블록의 2열 표로 옮겨지고 `이름`이 `화면 이름`으로 바뀌어, 셋업이 첫 명세에서 "파트가 비어 있습니다"로 작업 전체가 실패한다(`SetupCollector.toParts`).
바뀐 구조에서 명세를 읽고, 표가 잘못된 명세는 그 명세만 빼고 스프린트 경고로 남긴 뒤 나머지를 계속 처리한다. 관련 API는 operationId 대신 `메서드 URI` 목록으로 저장하고 OpenAPI 발췌에 쓴다.

## 점검 결과 (계획 근거)

| # | 항목 | 결과 | 근거 |
|---|---|---|---|
| 1 | 명세 DB 속성 | `화면 ID`(title), `화면 이름`(text), `최종 검증일`(date), `스프린트`(relation). 48행 | Notion 커넥터 data source `3fa8911b…` 조회 |
| 2 | 명세 DB ID | data source의 상위 DB가 설정값 `43fc286e…`와 같다. `requireParent`, API 버전 `2022-06-28`은 그대로 둔다 | 같은 조회, `application.yml:64-66` |
| 3 | 스프린트 DB | 코드가 읽는 6개 속성 그대로 | data source `e37bd9cb…` 조회 |
| 4 | 본문 첫 블록 | 표본 4개(QUIZ-02, MY-04, ERR-01, AI-01) 모두 2열 `table`, 헤더 행 `항목 \| 값`. 첫 열은 굵게 | 표본 페이지 조회 |
| 5 | `관련 API` 셀 | 인라인 코드 조각 사이 줄바꿈, 줄 앞 `  • `. REST 원문의 rich_text 조각 경계는 확인 못 함 → 조각 plain_text를 이어 붙인 뒤 나눠 조각 경계와 무관하게 만든다 | 같은 조회(`<br />`로 표시) |
| 6 | `Figma` 셀 | 텍스트는 화면 ID, 링크 URL이 프레임 주소 | 같은 조회 |
| 7 | 표본 API 13개 대 dev OpenAPI | 13개 모두 메서드, 경로가 `paths` 키와 글자 그대로 일치 | `/v3/api-docs` 대조 |
| 8 | 제목 레벨 의존 | 없음. `NotionBlockRenderer`만 heading을 다루고 H3는 `###`로 그대로 나간다. user 프롬프트의 `## 기능 명세 본문` 아래에 H3가 들어가 오히려 계층이 맞는다 | `NotionBlockRenderer.java:69-71`, grep |
| 9 | 표 셀 줄바꿈 | `renderTable`이 셀 안 `\n`을 그대로 두어 마크다운 표 행이 깨진다 | `NotionBlockRenderer.java:96-98` |
| 10 | `feature.related_operation_ids` 읽는 곳 | 없음(쓰기만) | grep `getRelatedOperationIds`, `related_operation_ids` |
| 11 | 스프린트 경고 노출 | `sprint.warnings`는 있으나 `GET /sprints/{id}` 응답에 없다 | `SprintResponse`, `docs/api/openapi.yaml:836-854` |
| 12 | 기존 동작 중 바뀌는 테스트 | `SetupJobHandlerIntegrationTest.모르는_파트_라벨이면_라벨_원문을_남기고_재시도_없이_실패한다`는 정책 변경으로 "그 명세만 빼고 계속"으로 바뀐다 | `SetupJobHandlerIntegrationTest.java:446` |

## 영향 범위
### 신규 파일
- `src/main/java/gravit/qa/adapter/notion/support/NotionSpecTableParser.java` - 본문 첫 표에서 6개 항목을 읽는 파서
- `src/main/java/gravit/qa/adapter/notion/dto/internal/NotionSpecTableDto.java` - 표 6개 항목 값
- `src/main/java/gravit/qa/adapter/exception/InvalidNotionSpecException.java` - 명세 1건을 읽지 못한 사유
- `src/main/java/gravit/qa/sprint/dto/response/WarningResponse.java` - 스프린트 경고 응답 요소
- `src/main/resources/db/migration/V3__rename_feature_related_apis.sql` - 컬럼 이름 변경, 예전 값 비우기
- `src/test/java/gravit/qa/adapter/notion/fixture/NotionSpecPageFixture.java` - 표본 4개의 Notion REST 응답(본문 블록, 표 행) 고정
- `src/test/java/gravit/qa/adapter/notion/support/NotionSpecTableParserTest.java` - 파서 순수 JUnit
- `src/test/java/gravit/qa/adapter/notion/support/NotionBlockRendererTest.java` - 표 셀 줄바꿈 순수 JUnit

### 수정 파일
- `src/main/java/gravit/qa/adapter/notion/infrastructure/NotionClient.java` - `SPEC_NAME`을 `화면 이름`으로, 명세 속성 상수 5개와 `toSpec`의 해당 읽기 삭제
- `src/main/java/gravit/qa/adapter/notion/dto/internal/NotionSpecDto.java` - 컴포넌트 5개와 `relatedOperationIds()` 삭제
- `src/main/java/gravit/qa/adapter/notion/support/NotionBlockRenderer.java` - 표 셀 안 줄바꿈을 `<br>`로
- `src/main/java/gravit/qa/adapter/openapi/support/OpenApiExtractor.java` - operationId 대신 `메서드 경로`로 매칭
- `src/main/java/gravit/qa/adapter/openapi/infrastructure/OpenApiClient.java` - `extract` 파라미터 이름 변경
- `src/main/java/gravit/qa/adapter/openapi/dto/internal/OpenApiExcerptDto.java` - `foundApis`, `missingApis`로 이름 변경
- `src/main/java/gravit/qa/feature/domain/Feature.java` - `relatedOperationIds` → `relatedApis`(`related_apis`)
- `src/main/java/gravit/qa/feature/dto/internal/FeatureUpsertDto.java` - 같은 이름 변경
- `src/main/java/gravit/qa/sprint/domain/WarningCode.java` - `SPEC_READ_FAILED` 추가
- `src/main/java/gravit/qa/sprint/domain/Sprint.java` - `completePreparation`이 셋업 경고를 교체
- `src/main/java/gravit/qa/sprint/service/SprintCommandService.java` - `applyPreparation`에 셋업 경고 파라미터
- `src/main/java/gravit/qa/sprint/facade/SetupFacade.java` - 셋업 경고 전달
- `src/main/java/gravit/qa/sprint/handler/SetupJobHandler.java` - `SetupResultDto`에 셋업 경고 전달
- `src/main/java/gravit/qa/sprint/support/SetupCollector.java` - 표 파싱, 명세 단위 실패, 스냅샷 형식, OpenAPI 발췌 입력
- `src/main/java/gravit/qa/sprint/support/SetupPromptBuilder.java` - 라우트 출처, 관련 API 줄, 이름 변경 반영
- `src/main/java/gravit/qa/sprint/dto/internal/SetupFeatureSpecDto.java` - 표 DTO 추가
- `src/main/java/gravit/qa/sprint/dto/internal/SetupFeatureDraftDto.java` - `toUpsertDto` 출처 변경
- `src/main/java/gravit/qa/sprint/dto/internal/SetupNotionDto.java` - 스프린트 경고 추가
- `src/main/java/gravit/qa/sprint/dto/internal/SetupResultDto.java` - 스프린트 경고 추가
- `src/main/java/gravit/qa/sprint/dto/response/SprintResponse.java` - `warnings` 추가
- `src/main/java/gravit/qa/sprint/controller/docs/SprintControllerDocs.java` - `getSprint` 설명에 경고 추가
- `src/main/resources/prompts/checklist-draft-user.txt` - `관련 API` 줄 추가
- `src/main/resources/prompts/checklist-draft-system.txt` - "어떤 operationId" 문구 변경
- `src/test/java/gravit/qa/sprint/fixture/SetupStubFixture.java` - 명세 페이지 4속성, 본문 첫 표 스텁
- `src/test/java/gravit/qa/adapter/notion/infrastructure/NotionClientIntegrationTest.java` - 명세 속성, 표본 본문 파싱
- `src/test/java/gravit/qa/adapter/openapi/support/OpenApiExtractorTest.java` - 메서드 경로 매칭
- `src/test/java/gravit/qa/adapter/openapi/infrastructure/OpenApiClientIntegrationTest.java` - 인자 형식
- `src/test/java/gravit/qa/sprint/support/SetupPromptBuilderTest.java` - DTO 생성, 관련 API 줄
- `src/test/java/gravit/qa/sprint/handler/SetupJobHandlerIntegrationTest.java` - 명세 단위 실패, 관련 API 저장, LLM 입력
- `src/test/java/gravit/qa/sprint/controller/SprintControllerIntegrationTest.java` - 응답 `warnings`
- `.claude/spec/qa-policy/setup-job.md` - **정책 변경**: 2단계 실패 정책, 라벨 출처, 기본 정보 표 규칙, 5단계 매칭, 스냅샷 형식, LLM 입력, 스프린트 경고
- `.claude/spec/qa-policy/checklist.md` - 경고 저장 위치 표의 스프린트 행에 `SPEC_READ_FAILED`
- `.claude/spec/qa-policy/external-adapters.md` - Notion 절에 명세 속성과 표 읽기 위치
- `docs/design/03_DESIGN.md` - 185행 `related_apis`, 301행 매칭 기준
- `docs/design/02_PREP_GUIDE.md` - 124-140행 ① 기능 명세 DB 속성 표와 본문 템플릿을 바뀐 구조로
- `docs/api/openapi.yaml` - `Sprint.warnings`, `ReportWarning.code`에 `SPEC_READ_FAILED`
- `docs/deploy-runbook.md` - 6단계에 스프린트 경고 확인, 6-a 문구(operationId → 관련 API)

## 구현 계획

1. **Entity / Flyway**
   - `V3__rename_feature_related_apis.sql`
     ```sql
     -- V3__rename_feature_related_apis.sql

     -- 관련 API 저장 형식이 operationId에서 "메서드 URI"로 바뀌어 컬럼 이름을 맞춘다
     ALTER TABLE feature RENAME COLUMN related_operation_ids TO related_apis;

     -- 예전 형식(operationId) 값이 새 형식과 섞이지 않게 비운다. 다음 준비에서 다시 채운다
     UPDATE feature SET related_apis = '{}';
     ```
   - `Feature`: `@Column(name = "related_apis", nullable = false) private List<String> relatedApis;` 빌더 파라미터, `create`, `update`의 `relatedOperationIds`를 `relatedApis`로 바꾼다
   - `Sprint`
     - 상수 `private static final Set<WarningCode> SETUP_WARNING_CODES = Set.of(WarningCode.SPEC_READ_FAILED);`
     - `completePreparation(LocalDate periodStart, LocalDate periodEnd, String figmaFileKey, List<WarningDto> setupWarnings, LocalDateTime now)`: 기존 갱신에 더해 `warnings`에서 `SETUP_WARNING_CODES` 코드를 뺀 목록 + `setupWarnings`로 교체한다. 다시 준비하면 이전 준비의 `SPEC_READ_FAILED`가 남지 않고, 종료 시 생기는 `NOTION_WRITE_FAILED`는 유지된다
   - `WarningCode`: `SPEC_READ_FAILED` 추가

2. **Adapter (Notion)**
   - `NotionClient`
     - `SPEC_NAME = "화면 이름"`. `SPEC_PARTS`, `SPEC_ROUTE`, `SPEC_RELATED_API`, `SPEC_FIGMA`, `SPEC_STATUS` 삭제
     - `toSpec(JsonNode page)`: `new NotionSpecDto(pageId, screenId.trim(), text(화면 이름), dateStart(최종 검증일), page.toString())`. `화면 ID`가 비면 지금처럼 `ExternalResponses.invalidResponse`(재시도 없음)
     - `getSpec`, `getPageBody`, `getTargetSpecs`는 시그니처 유지. 표 파싱은 하지 않는다(실패를 화면 ID와 함께 기록하려고 호출자 쪽에서 파싱)
   - `NotionSpecDto(String pageId, String screenId, String name, LocalDate lastVerifiedAt, String rawJson)` - `relatedOperationIds()`와 `OPERATION_ID_DELIMITER` 삭제
   - `NotionSpecTableDto(String specStatus, List<String> parts, String route, List<String> relatedApis, String figmaUrl, List<String> feSpecIds)` - 정적 팩토리 `of(...)`, 목록은 `List.copyOf`
   - `InvalidNotionSpecException extends RuntimeException` - `InvalidNotionSpecException(String reason)`. 사유는 사람이 읽는 한국어 문장
   - `NotionSpecTableParser` (`@NoArgsConstructor(access = PRIVATE)` final 유틸, `NotionBlockRenderer`와 같은 형태)
     - 공개 상수: `STATUS_ITEM = "명세 상태"`, `PARTS_ITEM = "파트"`, `ROUTE_ITEM = "라우트"`, `RELATED_API_ITEM = "관련 API"`, `FIGMA_ITEM = "Figma"`, `FE_SPEC_ID_ITEM = "FE 스펙 ID"`
     - 비공개 상수: `TABLE = "table"`, `TABLE_ROW = "table_row"`, `CELLS_FIELD = "cells"`, `PLAIN_TEXT_FIELD`, `HREF_FIELD`, `EMPTY_VALUE = "-"`, `ITEM_DELIMITER = Pattern.compile(",")`, `LINE_BREAK = Pattern.compile("\\R")`, `LIST_MARKER = Pattern.compile("^[\\s•\\-]+")`, `WHITESPACE = Pattern.compile("\\s+")`, `KEY_CELL = 0`, `VALUE_CELL = 1`
     - `public static NotionSpecTableDto parse(List<NotionBlockDto> blocks)`
       1. 첫 블록이 없거나 `type != table`이면 `InvalidNotionSpecException("본문 첫 블록이 기본 정보 표가 아닙니다")`
       2. `itemToCell(table)`: `table_row` 자식마다 첫 셀 plain_text를 trim한 값을 키로, 둘째 셀(JsonNode)을 값으로 담는다(`LinkedHashMap`, 같은 키는 처음 것). 헤더 행 `항목`과 모르는 행은 6개 키에 없으므로 쓰이지 않는다
       3. `명세 상태` 값이 비었으면 `InvalidNotionSpecException("기본 정보 표에 '명세 상태' 값이 없습니다")`
       4. `NotionSpecTableDto.of(value(상태), items(파트), value(라우트), lines(관련 API), href(Figma), items(FE 스펙 ID))`
     - `private static String value(JsonNode cell)`: rich_text 조각 plain_text를 이어 붙여 trim. 빈 문자열이거나 `-`이면 null
     - `private static List<String> items(JsonNode cell)`: `value`를 `,`로 나눠 trim, 빈 것 제외. null이면 빈 목록
     - `private static List<String> lines(JsonNode cell)`: `value`를 줄바꿈으로 나눔 → 줄마다 `LIST_MARKER` 제거 → `WHITESPACE`를 공백 1개로 → trim → 빈 줄 제외 → `distinct`
     - `private static String href(JsonNode cell)`: 조각 중 첫 번째 `href`가 있는 값. 없으면 null
   - `NotionBlockRenderer.renderTable`: 셀 문자열 `richText(cell).replace(LINE_BREAK, TABLE_CELL_LINE_BREAK)`(`TABLE_CELL_LINE_BREAK = "<br>"`)

3. **Adapter (OpenAPI)**
   - `OpenApiExtractor.extract(JsonNode document, Collection<String> apis)`: `wanted = new LinkedHashSet<>(apis)`. `paths`의 경로마다 `HTTP_METHODS`를 돌며 `key = method.toUpperCase(Locale.ROOT) + API_SEPARATOR + pathKey`(`API_SEPARATOR = " "`)가 `wanted`에 있으면 지금과 같이 발췌하고 `found.add(key)`. `missing = wanted - found`. 형식이 틀린 줄은 어떤 키와도 같지 않아 `missing`에 남는다. `OPERATION_ID_FIELD` 삭제
   - `OpenApiClient.extract(OpenApiDocumentDto apiDocs, Collection<String> apis)`
   - `OpenApiExcerptDto(JsonNode excerpt, List<String> foundApis, List<String> missingApis)`

4. **Service**
   - `SprintCommandService.applyPreparation(long sprintId, SetupBaselineDto baseline, LocalDate periodStart, LocalDate periodEnd, String figmaFileKey, List<WarningDto> setupWarnings, LocalDateTime now)` - `completePreparation`에 `setupWarnings` 전달. 나머지 동일
   - `FeatureCommandService.upsertAll`은 변경 없음(`notion_page_id` → `screen_id` 순 조회, UNIQUE 유지)

5. **Facade**: 기존 `SetupFacade.apply`만 수정 - `sprintCommandService.applyPreparation(..., result.sprintWarnings(), KstTimes.now(clock))`. 새 Facade 불필요

6. **Sprint support (셋업)**
   - `SetupCollector`
     - 상수 추가: `TABLE_FIELD = "table"`, `ERROR_FIELD = "error"`, `SPEC_READ_FAILED_MESSAGE = "%s: %s"`
     - `collectNotion`: 명세마다
       1. `getSpec`, `getPageBody`, 화면 ID 중복 검사(지금처럼 작업 실패)
       2. `try`: `NotionSpecTableDto table = NotionSpecTableParser.parse(body.blocks())` → `toParts(table)` → `toSpecStatus(table)` → `String markdown = bodyMarkdown(body)` → 스냅샷 `specSnapshot(spec, body, table, markdown, null)` → `NEEDS_CHECK`면 지금처럼 기능 경고 → `specs.add(SetupFeatureSpecDto.of(spec, table, parts, specStatus, markdown))`
       3. `catch (InvalidNotionSpecException e)`: `log.warn("기능 명세를 읽지 못해 이번 준비에서 뺍니다. screenId={}, reason={}", ...)` → 스냅샷 `specSnapshot(spec, body, null, body.markdown(), e.getMessage())` → `sprintWarnings.add(WarningDto.of(SPEC_READ_FAILED, "QUIZ-02: 사유"))`
       4. 반복 뒤 `specs`가 비면 `NonRetryableJobException("읽을 수 있는 대상 기능 명세가 없습니다: " + 사유들)`
       5. `SetupNotionDto.of(sprint, figmaFileKey, specs, snapshots, screenIdToWarnings, sprintWarnings)`
     - `toParts(NotionSpecTableDto table)`: 비면 `InvalidNotionSpecException("기본 정보 표에 '파트' 값이 없습니다")`, 모르는 라벨이면 `InvalidNotionSpecException("알 수 없는 파트 라벨 '" + label + "'")`
     - `toSpecStatus(NotionSpecTableDto table)`: 모르는 라벨이면 `InvalidNotionSpecException("알 수 없는 명세 상태 라벨 '" + label + "'")`(빈 값은 파서가 먼저 막는다)
     - `private String bodyMarkdown(NotionPageBodyDto body)`: `NotionBlockRenderer.toMarkdown(body.blocks().subList(1, body.blocks().size()))` - 파싱이 성공했으면 첫 블록은 표다
     - `specSnapshot(NotionSpecDto spec, NotionPageBodyDto body, NotionSpecTableDto table, String markdown, String error)`: `{page, table, markdown, blocks, error}`. `table`은 `objectMapper.valueToTree`, 실패면 `table`과 `error`만 채움(`table` null). 성공이면 `error`는 넣지 않는다
     - `collectOpenApi`: `List<String> apis = spec.table().relatedApis()`, 경고 메시지 `"dev OpenAPI에 없는 관련 API: " + String.join(DELIMITER, excerpt.missingApis())`
   - `SetupPromptBuilder.build`: `route`는 `spec.table().route()`, `Map.entry("relatedApis", list(spec.table().relatedApis()))` 추가(`", "`로 잇고 비면 `(없음)`), `openApiExcerpt`는 `excerpt.foundApis()`
   - `prompts/checklist-draft-user.txt`: `- 명세 상태:` 줄 다음에 `- 관련 API: {{relatedApis}}`
   - `prompts/checklist-draft-system.txt:11`: `어떤 operationId` → `어떤 API(메서드 경로)`

7. **DTO**
   - `SetupFeatureSpecDto(NotionSpecDto spec, NotionSpecTableDto table, List<Part> parts, SpecStatus specStatus, String bodyMarkdown)` - `of`도 같은 순서
   - `SetupFeatureDraftDto.toUpsertDto`: `route`는 `spec.table().route()`, 관련 API는 `spec.table().relatedApis()`
   - `FeatureUpsertDto`: `relatedOperationIds` → `relatedApis`
   - `SetupNotionDto`: 마지막 컴포넌트 `List<WarningDto> sprintWarnings` 추가(`List.copyOf`)
   - `SetupResultDto`: `figmaFileKey` 다음에 `List<WarningDto> sprintWarnings` 추가. `SetupJobHandler.handle`에서 `notion.sprintWarnings()` 전달
   - `WarningResponse(WarningCode code, String message)` - `@Schema`(code: 경고 코드, message: 사람이 읽는 설명, 둘 다 REQUIRED), `from(WarningDto)`
   - `SprintResponse`: `latestSetupJobId` 다음에 `@Schema(description = "스프린트 경고. 준비 중 읽지 못한 기능 명세(SPEC_READ_FAILED), 종료 시 Notion 기록 실패(NOTION_WRITE_FAILED)", requiredMode = REQUIRED) List<WarningResponse> warnings`. `of`에서 `sprint.warnings().stream().map(WarningResponse::from).toList()`

8. **Controller**: 경로 변경 없음. `GET /api/v1/sprints/{sprintId}` 응답에 `warnings` 필드만 추가. `SprintControllerDocs.getSprint` description 끝을 "화면 안내(guide), 파트별 진행률, 스프린트 경고(warnings)를 함께 내려준다"로

9. **문서**
   - `setup-job.md`
     - 단계 표 2행 실패 칸: "대상 기능 0개, 화면 ID 중복, 읽을 수 있는 명세 0개는 재시도 없이 실패. 기본 정보 표를 읽지 못한 명세(표 없음, 명세 상태나 파트 없음, 모르는 라벨)는 그 명세만 빼고 `SPEC_READ_FAILED` 스프린트 경고"
     - 단계 표 5행: "기능별 `관련 API`(메서드 URI)와 메서드, 경로가 정확히 같은 operation만 발췌"
     - "기능 명세 기본 정보 표" 절 신설: 위치(본문 첫 블록), 항목 6개 읽기 규칙(위 2번 표), `-` 처리
     - 라벨 변환 표 출처를 "기본 정보 표 `파트`, `명세 상태`"로. `이름` → `화면 이름`
     - 스냅샷 표 `NOTION_SPEC`: `{page, table, markdown, blocks}`, 실패면 `{page, table: null, markdown(전체), blocks, error}`
     - LLM 입력 표: 기능 속성에 관련 API 목록 추가, 명세 본문은 기본 정보 표를 뺀 블록
     - 기능 경고 표 `OPENAPI_OPERATION_MISSING` 뜻: "`관련 API`가 dev OpenAPI에 없거나 `메서드 경로` 형식이 아님". 스프린트 경고 표 신설(`SPEC_READ_FAILED`, 다시 준비하면 교체)
   - `checklist.md` 경고 저장 위치 표 스프린트 행: `NOTION_WRITE_FAILED`, `SPEC_READ_FAILED`
   - `external-adapters.md` Notion 절: "기능 명세 속성은 `화면 ID`, `화면 이름`, `최종 검증일`만 읽는다. 나머지는 본문 첫 표(`setup-job.md`)"
   - `03_DESIGN.md:185`: `related_apis (text[]) | Notion 본문 기본 정보 표 관련 API(메서드 URI)`, `:301`: "기능의 `관련 API`와 메서드, 경로가 같은 operation만 추출"
   - `02_PREP_GUIDE.md` ① 기능 명세 DB 속성 표를 4개로, 본문 템플릿 맨 앞에 기본 정보 표(6항목)와 H3 섹션(`화면에 보이는 것`, `사용자가 할 수 있는 것`, `예외 상황`)
   - `openapi.yaml`: `Sprint` 두 번째 객체 `required`에 `warnings`, `properties.warnings: {type: array, items: {$ref: '#/components/schemas/ReportWarning'}}`. `ReportWarning.code` enum에 `SPEC_READ_FAILED`
   - `deploy-runbook.md` 6단계: `jq '{status, latestSetupJobId, warnings}'`로 바꾸고 "`SPEC_READ_FAILED`가 있으면 그 화면 ID의 Notion 기본 정보 표를 고친 뒤 다시 준비" 안내. 6-a의 "없는 operationId" → "없는 관련 API"

## 결정 필요 (Decisions needed)
- [x] 명세 단위 실패 범위 - 표 없음, 명세 상태 없음에 더해 모르는 명세 상태 라벨, 파트 없음, 모르는 파트 라벨까지. 화면 ID 빈 값과 중복은 작업 실패 유지 (2026-11-10 승인)
- [x] 실패 기록 위치 - `sprint.warnings`에 `SPEC_READ_FAILED`(메시지에 화면 ID와 사유), `GET /sprints/{id}`에 `warnings` 추가. 실패 명세는 `feature` upsert에서 빠져 기존 행 유지 (2026-11-10 승인)
- [x] 읽을 수 있는 명세가 0개면 작업 실패 (2026-11-10 승인)
- [x] 관련 API 매칭 - 메서드, 경로 정확 일치. `OPENAPI_OPERATION_MISSING` 코드 이름 유지 (2026-11-10 승인)
- [x] DB - V3에서 `related_operation_ids` → `related_apis` 이름 변경 후 비우기 (2026-11-10 승인)
- [x] 렌더링 - 기본 정보 표는 LLM 본문에서 빼고 기능 속성으로, 표 셀 줄바꿈은 `<br>` (2026-11-10 승인)

## 검증
- 대상 테스트
  - `NotionSpecTableParserTest`(순수 JUnit, `NotionSpecPageFixture`의 REST 응답 JSON을 블록 트리로 조립)
    - QUIZ-02: `확정`, 파트 3개, 라우트 `/learning/lessons/$lessonId`, 관련 API 2개(`GET /api/v1/problems/{lessonId}`, `POST /api/v1/problems/results`), Figma URL, FE 스펙 ID 빈 목록
    - MY-04: 관련 API 11개를 셀 순서대로
    - ERR-01: 라우트 null, 관련 API 빈 목록, FE 스펙 ID `[E.1]`
    - AI-01: `확인 필요`, 서식 없는 표(굵게, 인라인 코드 없음), 예전 형식 본문에서도 표를 읽는다
    - 첫 블록이 표가 아니면 사유를 담아 실패, `명세 상태` 행이 없거나 `-`면 실패
    - 줄 앞 `-` 기호와 공백, 한 조각 안 `\n`과 여러 조각으로 나뉜 줄이 같은 결과
  - `NotionBlockRendererTest`: 표 셀 안 줄바꿈이 `<br>`로 바뀌어 행이 한 줄로 유지된다
  - `NotionClientIntegrationTest`
    - 명세 속성: `화면 ID`, `화면 이름`, `최종 검증일`을 읽는다
    - QUIZ-02 본문(페이지 자식, 표 자식 스텁)을 `getPageBody`로 받아 `NotionSpecTableParser.parse`가 같은 값을 낸다
  - `OpenApiExtractorTest`: `GET /api/v1/units/{unitId}`는 발췌, 메서드만 다른 줄과 형식이 틀린 줄은 `missingApis`
  - `OpenApiClientIntegrationTest`: `GET /api/v1/version`으로 발췌
  - `SetupPromptBuilderTest`: user 프롬프트에 `- 관련 API: GET …` 줄, 라우트가 표 값, 기본 정보 표가 본문에 없다
  - `SetupJobHandlerIntegrationTest`
    - 성공 흐름 기존 테스트가 새 스텁(4속성 + 본문 첫 표)으로 통과
    - LRN-02 `feature.related_apis`가 `메서드 URI` 목록으로 저장되고, 없는 API에 `OPENAPI_OPERATION_MISSING`
    - 신규 `@Nested 명세_읽기_실패`
      - 표 없는 명세는 빼고 나머지 명세로 `REVIEW`, 스프린트 경고 `SPEC_READ_FAILED`(메시지에 화면 ID), 빠진 명세의 `NOTION_SPEC` 스냅샷은 남는다
      - 모르는 파트 라벨도 그 명세만 빠지고 메시지에 `'모바일'`(기존 `모르는_파트_라벨이면_라벨_원문을_남기고_재시도_없이_실패한다` 대체)
      - 모든 명세를 읽지 못하면 재시도 없이 `FAILED`, 스프린트 `PREPARE_FAILED`
      - 다시 준비해 성공하면 이전 `SPEC_READ_FAILED`가 남지 않는다
  - `SprintControllerIntegrationTest`: `GET /sprints/{id}` 응답 `warnings`에 `NOTION_WRITE_FAILED`(기존 종료 테스트에 단언 추가)
- 전체: `./gradlew test`(Flyway V3 적용과 `Feature` 매핑 포함)

## Deviation Log
> implement 스킬이 구현 중 계획을 벗어난 지점을 여기에 기록한다. (작성 시점엔 비워둔다)
- `src/main/java/gravit/qa/sprint/domain/Sprint.java`: `SETUP_WARNING_CODES`를 `Set.of` 대신 `EnumSet.of`로, 교체 로직을 `private replaceSetupWarnings`로 분리 — 이유: 같은 클래스의 `PREPARABLE_STATUSES`와 표기를 맞추고 `completePreparation`을 상태 갱신만 보이게 둠
- `src/main/java/gravit/qa/sprint/support/SetupCollector.java`: `private saveSpec(long, long, NotionSpecDto, String)` 추가 — 이유: 성공, 실패 두 갈래가 같은 `NOTION_SPEC` 저장 호출(인자 7개)을 반복함
- `src/main/java/gravit/qa/sprint/support/SetupPromptBuilder.java`: `private listOrNone(List<String>)` 추가 — 이유: 관련 API 목록의 빈 값 표기를 `orNone`과 같은 `(없음)`으로 맞춤
- `docs/design/03_DESIGN.md`, `docs/design/02_PREP_GUIDE.md`, `docs/api/openapi.yaml`: 계획대로 고쳤으나 `.gitignore`의 `/docs/*` 대상이라 로컬에만 남고 커밋에 들어가지 않음(추적되는 문서는 `docs/deploy-runbook.md`뿐) — 이유: 저장소 설정상 원래 추적하지 않는 파일
