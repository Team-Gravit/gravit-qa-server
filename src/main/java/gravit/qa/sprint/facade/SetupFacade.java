package gravit.qa.sprint.facade;

import gravit.qa.checklist.dto.internal.CellJudgmentDto;
import gravit.qa.checklist.dto.internal.ChecklistItemDraftDto;
import gravit.qa.checklist.service.ChecklistCommandService;
import gravit.qa.designReview.dto.internal.DesignReviewItemDraftDto;
import gravit.qa.designReview.service.DesignReviewCommandService;
import gravit.qa.feature.dto.internal.SpecPageDto;
import gravit.qa.feature.dto.internal.SprintFeatureSpecDto;
import gravit.qa.feature.service.FeatureCommandService;
import gravit.qa.global.annotation.Facade;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.global.util.KstTimes;
import gravit.qa.job.domain.JobStatus;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.dto.internal.JobDto;
import gravit.qa.job.service.JobCommandService;
import gravit.qa.job.service.JobQueryService;
import gravit.qa.reflection.dto.internal.ReflectionProposalDto;
import gravit.qa.reflection.service.ReflectionCommandService;
import gravit.qa.reflection.service.ReflectionQueryService;
import gravit.qa.snapshot.service.SnapshotCommandService;
import gravit.qa.sprint.domain.SetupStepKey;
import gravit.qa.sprint.domain.SetupStepStatus;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.ConnectResultDto;
import gravit.qa.sprint.dto.internal.SetupSnapshotDto;
import gravit.qa.sprint.dto.internal.SpecCollectDto;
import gravit.qa.sprint.dto.internal.SprintDto;
import gravit.qa.sprint.dto.response.SetupJobStartResponse;
import gravit.qa.sprint.service.PrepareUnitService;
import gravit.qa.sprint.service.SetupStepService;
import gravit.qa.sprint.service.SprintCommandService;
import gravit.qa.sprint.service.SprintQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static gravit.qa.global.exception.domain.CustomErrorCode.JOB_ALREADY_ACTIVE;
import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_STATUS_CONFLICT;

@Facade
@RequiredArgsConstructor
public class SetupFacade {

    private static final Set<JobStatus> RESTARTABLE_JOB_STATUSES = Set.of(JobStatus.FAILED, JobStatus.CANCELLED);
    private static final String WAITING_MESSAGE = "반영 승인 대기 %d건";
    private static final String REFLECTIONS_DONE_MESSAGE = "수정 이력 반영 제안을 모두 처리했습니다";

    private final SprintQueryService sprintQueryService;
    private final SprintCommandService sprintCommandService;
    private final SetupStepService setupStepService;
    private final PrepareUnitService prepareUnitService;
    private final FeatureCommandService featureCommandService;
    private final SnapshotCommandService snapshotCommandService;
    private final ReflectionQueryService reflectionQueryService;
    private final ReflectionCommandService reflectionCommandService;
    private final ChecklistCommandService checklistCommandService;
    private final DesignReviewCommandService designReviewCommandService;
    private final JobQueryService jobQueryService;
    private final JobCommandService jobCommandService;

    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public SetupJobStartResponse startSetup(
            long sprintId,
            int version
    ) {
        SprintDto sprint = sprintQueryService.getSprint(sprintId);
        if (jobQueryService.hasActiveJob(sprintId)) {
            throw new RestApiException(JOB_ALREADY_ACTIVE);
        }
        boolean lastJobFailed = jobQueryService.findLatestJob(sprintId)
                .map(JobDto::status)
                .filter(RESTARTABLE_JOB_STATUSES::contains)
                .isPresent();

        Long jobId = transactionTemplate.execute(status -> switch (sprint.status()) {
            case DRAFT -> {
                sprintCommandService.startSpecLoad(sprintId, version);
                yield jobCommandService.enqueue(JobType.SPEC_LOAD, sprintId, Map.of());
            }
            case SPEC_LOAD -> {
                sprintCommandService.requireVersion(sprintId, version);
                if (!lastJobFailed && setupStepService.getStatus(sprintId, SetupStepKey.SPEC_COLLECT) != SetupStepStatus.WAITING) {
                    throw new RestApiException(SPRINT_STATUS_CONFLICT);
                }
                if (!lastJobFailed) {
                    reflectionCommandService.deleteUnapplied(sprintId);
                    prepareUnitService.deleteAll(sprintId, SetupStepKey.SPEC_COLLECT);
                    setupStepService.reset(sprintId, SetupStepKey.SPEC_COLLECT);
                }
                yield jobCommandService.enqueue(JobType.SPEC_LOAD, sprintId, Map.of());
            }
            case CHECKLIST, DESIGN_REVIEW, AUTO_REVIEW -> {
                sprintCommandService.requireVersion(sprintId, version);
                if (!lastJobFailed) {
                    throw new RestApiException(SPRINT_STATUS_CONFLICT);
                }
                yield jobCommandService.enqueue(JobType.QA_PREPARE, sprintId, Map.of());
            }
            default -> throw new RestApiException(SPRINT_STATUS_CONFLICT);
        });

        return SetupJobStartResponse.of(jobId);
    }

    public void recordConnect(
            long sprintId,
            ConnectResultDto result
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            sprintCommandService.applyConnect(
                    sprintId,
                    result.notionSprint() == null ? null : result.notionSprint().changeSummary(),
                    result.figmaFileKey(),
                    result.connections(),
                    result.notionSprint() != null,
                    KstTimes.now(clock)
            );
            if (result.isSuccess()) {
                setupStepService.finish(sprintId, SetupStepKey.CONNECT, SetupStepStatus.DONE, null, KstTimes.now(clock));
            }
        });
    }

    public List<Long> saveSnapshots(
            long sprintId,
            long jobId,
            List<SetupSnapshotDto> snapshots
    ) {
        return transactionTemplate.execute(status -> snapshotCommandService.saveAll(sprintId, jobId, snapshots.stream()
                .map(SetupSnapshotDto::toCreateDto)
                .toList()));
    }

    public Map<String, Long> saveSpecs(
            long sprintId,
            long jobId,
            String sprintKey,
            SpecCollectDto collected
    ) {
        return transactionTemplate.execute(status -> {
            Map<String, Long> screenIdToFeatureId = featureCommandService.upsertAll(collected.pages().stream()
                    .map(SpecPageDto::toUpsertDto)
                    .toList());
            List<SprintFeatureSpecDto> specs = collected.pages().stream()
                    .map(page -> page.toSprintFeatureSpec(screenIdToFeatureId.get(page.screenId()), sprintKey))
                    .toList();
            featureCommandService.replaceSprintFeatures(sprintId, specs);
            snapshotCommandService.saveAll(sprintId, jobId, collected.snapshots().stream()
                    .map(SetupSnapshotDto::toCreateDto)
                    .toList());
            return screenIdToFeatureId;
        });
    }

    public void saveReflectionProposal(
            long sprintId,
            long featureId,
            String screenId,
            ReflectionProposalDto proposal
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            reflectionCommandService.save(sprintId, featureId, proposal);
            prepareUnitService.finish(sprintId, SetupStepKey.SPEC_COLLECT, screenId, Map.of(
                    "promptChars", proposal.promptChars(),
                    "operations", proposal.operations().size(),
                    "appliable", proposal.appliable()
            ), KstTimes.now(clock));
        });
    }

    public void finishSpecCollect(
            long sprintId,
            String message
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            long unresolved = reflectionQueryService.countUnresolved(sprintId);
            if (unresolved > 0) {
                setupStepService.finish(sprintId, SetupStepKey.SPEC_COLLECT, SetupStepStatus.WAITING, message + ". " + WAITING_MESSAGE.formatted(unresolved), KstTimes.now(clock));
                return;
            }
            moveToQaPrepare(sprintId, message);
        });
    }

    public void proceedAfterReflections(long sprintId) {
        transactionTemplate.executeWithoutResult(status -> {
            SprintDto sprint = sprintQueryService.getSprint(sprintId);
            if (sprint.status() != SprintStatus.SPEC_LOAD
                    || setupStepService.getStatus(sprintId, SetupStepKey.SPEC_COLLECT) != SetupStepStatus.WAITING) {
                return;
            }
            long unresolved = reflectionQueryService.countUnresolved(sprintId);
            if (unresolved > 0) {
                setupStepService.finish(sprintId, SetupStepKey.SPEC_COLLECT, SetupStepStatus.WAITING, WAITING_MESSAGE.formatted(unresolved), KstTimes.now(clock));
                return;
            }
            moveToQaPrepare(sprintId, REFLECTIONS_DONE_MESSAGE);
        });
    }

    public void saveChecklistUnit(
            long sprintId,
            long featureId,
            String screenId,
            List<ChecklistItemDraftDto> items,
            List<Long> sourceRefs,
            Map<String, Object> detail
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            checklistCommandService.saveGenerated(sprintId, featureId, items, sourceRefs);
            prepareUnitService.finish(sprintId, SetupStepKey.CHECKLIST_GENERATE, screenId, detail, KstTimes.now(clock));
        });
    }

    public void saveDesignReviewUnit(
            long sprintId,
            long featureId,
            String screenId,
            List<DesignReviewItemDraftDto> items,
            List<String> figmaNodeIds,
            SetupSnapshotDto snapshot,
            long jobId,
            Map<String, Object> detail
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            designReviewCommandService.saveJudged(sprintId, featureId, items);
            featureCommandService.updateFigmaNodeIds(sprintId, featureId, figmaNodeIds);
            if (snapshot != null) {
                snapshotCommandService.saveAll(sprintId, jobId, List.of(snapshot.toCreateDto()));
            }
            prepareUnitService.finish(sprintId, SetupStepKey.DESIGN_REVIEW, screenId, detail, KstTimes.now(clock));
        });
    }

    public void saveAutoReviewUnit(
            long sprintId,
            String unitKey,
            List<CellJudgmentDto> judgments,
            Map<String, Object> detail
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            checklistCommandService.judgeCells(judgments);
            prepareUnitService.finish(sprintId, SetupStepKey.AUTO_REVIEW, unitKey, detail, KstTimes.now(clock));
        });
    }

    public void finishQaStep(
            long sprintId,
            SetupStepKey key,
            String message
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            sprintCommandService.advanceFrom(sprintId, key.getSprintStatus());
            setupStepService.finish(sprintId, key, SetupStepStatus.DONE, message, KstTimes.now(clock));
        });
    }

    private void moveToQaPrepare(
            long sprintId,
            String message
    ) {
        sprintCommandService.advanceFrom(sprintId, SprintStatus.SPEC_LOAD);
        setupStepService.finish(sprintId, SetupStepKey.SPEC_COLLECT, SetupStepStatus.DONE, message, KstTimes.now(clock));
        jobCommandService.enqueue(JobType.QA_PREPARE, sprintId, Map.of());
    }
}
