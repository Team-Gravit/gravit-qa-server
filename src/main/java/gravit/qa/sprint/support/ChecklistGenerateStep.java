package gravit.qa.sprint.support;

import gravit.qa.adapter.openapi.dto.internal.OpenApiDocumentDto;
import gravit.qa.adapter.openapi.dto.internal.OpenApiExcerptDto;
import gravit.qa.adapter.openapi.infrastructure.OpenApiClient;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.ChecklistGenerationDto;
import gravit.qa.checklist.support.ChecklistGenerator;
import gravit.qa.feature.dto.internal.SprintFeatureDto;
import gravit.qa.feature.service.FeatureQueryService;
import gravit.qa.global.util.KstTimes;
import gravit.qa.job.support.JobContext;
import gravit.qa.snapshot.domain.SnapshotSource;
import gravit.qa.snapshot.infrastructure.SnapshotFileStore;
import gravit.qa.sprint.domain.SetupStepKey;
import gravit.qa.sprint.dto.internal.SetupSnapshotDto;
import gravit.qa.sprint.dto.internal.SetupTargetDto;
import gravit.qa.sprint.facade.SetupFacade;
import gravit.qa.sprint.service.PrepareUnitService;
import gravit.qa.sprint.service.SprintQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ChecklistGenerateStep {

    private static final String OPENAPI_FILE = "api-docs.json";
    private static final String DONE_MESSAGE = "화면 %d개 체크리스트 생성(이번 실행 %d개: 항목 %d개, 1차 %d개, 2차 %d개, 초안 생성 실패 %d개, 입력 잘림 %d개, OpenAPI에 없는 API %d개)";

    private final SetupFacade setupFacade;

    private final FeatureQueryService featureQueryService;
    private final SprintQueryService sprintQueryService;
    private final PrepareUnitService prepareUnitService;

    private final ChecklistGenerator checklistGenerator;
    private final OpenApiClient openApiClient;
    private final SnapshotFileStore snapshotFileStore;

    private final Clock clock;

    public void run(
            JobContext context,
            SetupTargetDto target
    ) {
        long sprintId = target.sprintId();
        List<SprintFeatureDto> features = featureQueryService.getSprintFeatures(sprintId);
        String changeSummary = sprintQueryService.getSprint(sprintId).changeSummary();

        OpenApiDocumentDto apiDocs = openApiClient.fetchApiDocs();
        SetupSnapshotDto snapshot = SetupSnapshotDto.of(
                SnapshotSource.OPENAPI,
                apiDocs.url(),
                null,
                snapshotFileStore.save(sprintId, context.jobId(), SnapshotSource.OPENAPI, OPENAPI_FILE, apiDocs.rawJson()),
                KstTimes.now(clock)
        );
        List<Long> sourceRefs = setupFacade.saveSnapshots(sprintId, context.jobId(), List.of(snapshot));

        Set<String> done = prepareUnitService.findDoneUnitKeys(sprintId, SetupStepKey.CHECKLIST_GENERATE);
        int screens = 0;
        int items = 0;
        int autoItems = 0;
        int failed = 0;
        int truncated = 0;
        int missingApis = 0;
        for (SprintFeatureDto feature : features) {
            if (done.contains(feature.screenId())) {
                continue;
            }
            context.checkCancelled();
            OpenApiExcerptDto excerpt = feature.feature().relatedApis().isEmpty() ? null : openApiClient.extract(apiDocs, feature.feature().relatedApis());
            ChecklistGenerationDto generation = checklistGenerator.generate(feature, excerpt, changeSummary);

            long auto = generation.items().stream().filter(item -> item.classification() == Classification.AUTO).count();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("items", generation.items().size());
            detail.put("autoItems", auto);
            detail.put("promptChars", generation.promptChars());
            detail.put("truncated", generation.truncated());
            detail.put("failed", generation.failed());
            detail.put("missingApis", excerpt == null ? List.of() : excerpt.missingApis());
            setupFacade.saveChecklistUnit(sprintId, feature.featureId(), feature.screenId(), generation.items(), sourceRefs, detail);

            screens++;
            items += generation.items().size();
            autoItems += (int) auto;
            failed += generation.failed() ? 1 : 0;
            truncated += generation.truncated() ? 1 : 0;
            missingApis += excerpt == null ? 0 : excerpt.missingApis().size();
        }

        String message = DONE_MESSAGE.formatted(features.size(), screens, items, autoItems, items - autoItems, failed, truncated, missingApis);
        setupFacade.finishQaStep(sprintId, SetupStepKey.CHECKLIST_GENERATE, message);
    }
}
