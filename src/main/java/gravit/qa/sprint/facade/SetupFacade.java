package gravit.qa.sprint.facade;

import gravit.qa.checklist.dto.internal.ChecklistProposalDto;
import gravit.qa.checklist.service.ChecklistCommandService;
import gravit.qa.feature.dto.internal.SprintFeatureCreateDto;
import gravit.qa.feature.service.FeatureCommandService;
import gravit.qa.global.annotation.Facade;
import gravit.qa.global.util.KstTimes;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.dto.response.JobResponse;
import gravit.qa.job.service.JobCommandService;
import gravit.qa.job.service.JobQueryService;
import gravit.qa.snapshot.service.SnapshotCommandService;
import gravit.qa.sprint.dto.internal.SetupFeatureDraftDto;
import gravit.qa.sprint.dto.internal.SetupResultDto;
import gravit.qa.sprint.dto.internal.SetupSnapshotDto;
import gravit.qa.sprint.service.SprintCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Facade
@RequiredArgsConstructor
public class SetupFacade {

    private final SprintCommandService sprintCommandService;
    private final FeatureCommandService featureCommandService;
    private final SnapshotCommandService snapshotCommandService;
    private final ChecklistCommandService checklistCommandService;
    private final JobCommandService jobCommandService;
    private final JobQueryService jobQueryService;

    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public JobResponse startSetup(long sprintId) {
        Long jobId = transactionTemplate.execute(status -> {
            sprintCommandService.startPreparing(sprintId);
            return jobCommandService.enqueue(JobType.SETUP, sprintId, Map.of());
        });

        return jobQueryService.getJob(jobId);
    }

    public void apply(
            long sprintId,
            long jobId,
            SetupResultDto result
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            sprintCommandService.applyPreparation(
                    sprintId,
                    result.baseline(),
                    result.periodStart(),
                    result.periodEnd(),
                    result.figmaFileKey(),
                    KstTimes.now(clock)
            );

            Map<String, Long> screenIdToFeatureId = featureCommandService.upsertAll(result.features().stream()
                    .map(SetupFeatureDraftDto::toUpsertDto)
                    .toList());
            featureCommandService.replaceSprintFeatures(sprintId, result.features().stream()
                    .map(feature -> SprintFeatureCreateDto.of(
                            screenIdToFeatureId.get(feature.screenId()),
                            feature.figmaNodeIds(),
                            feature.warnings()
                    ))
                    .toList());

            List<SetupSnapshotDto> snapshots = result.snapshots();
            List<Long> snapshotIds = snapshotCommandService.saveAll(sprintId, jobId, snapshots.stream()
                    .map(SetupSnapshotDto::toCreateDto)
                    .toList());

            checklistCommandService.replaceProposed(sprintId, result.features().stream()
                    .map(feature -> ChecklistProposalDto.of(
                            screenIdToFeatureId.get(feature.screenId()),
                            feature.items(),
                            sourceRefsOf(feature.screenId(), snapshots, snapshotIds)
                    ))
                    .toList());
        });
    }

    private List<Long> sourceRefsOf(
            String screenId,
            List<SetupSnapshotDto> snapshots,
            List<Long> snapshotIds
    ) {
        List<Long> sourceRefs = new ArrayList<>();
        for (int index = 0; index < snapshots.size(); index++) {
            SetupSnapshotDto snapshot = snapshots.get(index);
            if (snapshot.isShared() || screenId.equals(snapshot.screenId())) {
                sourceRefs.add(snapshotIds.get(index));
            }
        }
        return sourceRefs;
    }
}
