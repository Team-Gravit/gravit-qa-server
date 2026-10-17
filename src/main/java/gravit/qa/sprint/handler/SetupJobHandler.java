package gravit.qa.sprint.handler;

import gravit.qa.checklist.support.ChecklistDraftValidator;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.support.JobContext;
import gravit.qa.job.support.JobHandler;
import gravit.qa.job.support.NonRetryableJobException;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.SetupBaselineDto;
import gravit.qa.sprint.dto.internal.SetupDiffDto;
import gravit.qa.sprint.dto.internal.SetupFeatureDraftDto;
import gravit.qa.sprint.dto.internal.SetupFigmaDto;
import gravit.qa.sprint.dto.internal.SetupNotionDto;
import gravit.qa.sprint.dto.internal.SetupOpenApiDto;
import gravit.qa.sprint.dto.internal.SetupResultDto;
import gravit.qa.sprint.dto.internal.SetupSnapshotDto;
import gravit.qa.sprint.dto.internal.SetupTargetDto;
import gravit.qa.sprint.facade.SetupFacade;
import gravit.qa.sprint.service.SprintQueryService;
import gravit.qa.sprint.support.SetupCollector;
import gravit.qa.sprint.support.SetupInterpreter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SetupJobHandler implements JobHandler {

    private static final String BASELINE_STEP = "BASELINE";
    private static final String NOTION_STEP = "NOTION";
    private static final String FIGMA_STEP = "FIGMA";
    private static final String DIFF_STEP = "DIFF";
    private static final String OPENAPI_STEP = "OPENAPI";
    private static final String INTERPRET_STEP = "INTERPRET";
    private static final String VALIDATE_STEP = "VALIDATE";
    private static final String APPLY_STEP = "APPLY";
    private static final String DIFF_SKIP_REASON = "직전 기준점 없음 - 전체 범위";
    private static final String ERROR_DELIMITER = "; ";

    private final SetupFacade setupFacade;

    private final SprintQueryService sprintQueryService;

    private final SetupCollector setupCollector;
    private final SetupInterpreter setupInterpreter;
    private final ChecklistDraftValidator checklistDraftValidator;

    @Override
    public JobType type() {
        return JobType.SETUP;
    }

    @Override
    public void handle(JobContext context) {
        if (context.sprintId() == null) {
            throw new NonRetryableJobException("셋업 작업에 스프린트가 없습니다. jobId=" + context.jobId());
        }
        long sprintId = context.sprintId();
        long jobId = context.jobId();

        SetupTargetDto target = sprintQueryService.getSetupTarget(sprintId);
        if (target.status() != SprintStatus.PREPARING) {
            throw new NonRetryableJobException("준비 중인 스프린트가 아닙니다. sprintId=" + sprintId + ", status=" + target.status());
        }

        SetupBaselineDto baseline = context.step(BASELINE_STEP, () -> setupCollector.collectBaseline(sprintId));
        SetupNotionDto notion = context.step(NOTION_STEP, () -> setupCollector.collectNotion(sprintId, jobId, target.notionPageId()));
        SetupFigmaDto figma = context.step(FIGMA_STEP, () -> setupCollector.collectFigma(sprintId, jobId, notion.figmaFileKey(), target.sprintKey(), notion.specs()));
        SetupDiffDto diff = collectDiff(context, sprintId, jobId, baseline);
        SetupOpenApiDto openApi = context.step(OPENAPI_STEP, () -> setupCollector.collectOpenApi(sprintId, jobId, notion.specs()));

        List<SetupFeatureDraftDto> features = context.step(INTERPRET_STEP, () -> setupInterpreter.interpret(notion, figma, diff, openApi, context));
        context.step(VALIDATE_STEP, () -> validate(features));

        SetupResultDto result = SetupResultDto.of(
                baseline,
                notion.sprint().periodStart(),
                notion.sprint().periodEnd(),
                notion.figmaFileKey(),
                features,
                snapshotsOf(notion, figma, diff, openApi)
        );
        context.step(APPLY_STEP, () -> apply(sprintId, jobId, result));
    }

    private SetupDiffDto collectDiff(
            JobContext context,
            long sprintId,
            long jobId,
            SetupBaselineDto baseline
    ) {
        if (baseline.previousBaselineId() == null) {
            context.skipStep(DIFF_STEP, DIFF_SKIP_REASON);
            return SetupDiffDto.empty();
        }
        return context.step(DIFF_STEP, () -> setupCollector.collectDiff(sprintId, jobId, baseline));
    }

    private void validate(List<SetupFeatureDraftDto> features) {
        List<String> errors = new ArrayList<>();
        for (SetupFeatureDraftDto feature : features) {
            checklistDraftValidator.validateDrafts(feature.items(), feature.spec().parts())
                    .forEach(error -> errors.add(feature.screenId() + " " + error));
        }
        if (!errors.isEmpty()) {
            throw new NonRetryableJobException("체크 항목 초안이 참조 무결성 검사를 통과하지 못했습니다: " + String.join(ERROR_DELIMITER, errors));
        }
    }

    private void apply(
            long sprintId,
            long jobId,
            SetupResultDto result
    ) {
        try {
            setupFacade.apply(sprintId, jobId, result);
        } catch (IllegalStateException e) {
            throw new NonRetryableJobException(e.getMessage(), e);
        }
    }

    private List<SetupSnapshotDto> snapshotsOf(
            SetupNotionDto notion,
            SetupFigmaDto figma,
            SetupDiffDto diff,
            SetupOpenApiDto openApi
    ) {
        List<SetupSnapshotDto> snapshots = new ArrayList<>();
        snapshots.addAll(notion.snapshots());
        snapshots.addAll(figma.snapshots());
        snapshots.addAll(diff.snapshots());
        snapshots.add(openApi.snapshot());
        return snapshots;
    }
}
