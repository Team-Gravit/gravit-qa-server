package gravit.qa.sprint.handler;

import gravit.qa.adapter.notion.dto.internal.NotionHistoryRowDto;
import gravit.qa.feature.dto.internal.SpecPageDto;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.support.JobContext;
import gravit.qa.job.support.JobHandler;
import gravit.qa.job.support.NonRetryableJobException;
import gravit.qa.reflection.dto.internal.ReflectionProposalDto;
import gravit.qa.reflection.support.ReflectionProposer;
import gravit.qa.sprint.domain.SetupStepKey;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.ConnectResultDto;
import gravit.qa.sprint.dto.internal.SetupTargetDto;
import gravit.qa.sprint.dto.internal.SpecCollectDto;
import gravit.qa.sprint.facade.SetupFacade;
import gravit.qa.sprint.service.PrepareUnitService;
import gravit.qa.sprint.service.SprintQueryService;
import gravit.qa.sprint.support.ConnectCollector;
import gravit.qa.sprint.support.SetupStepRunner;
import gravit.qa.sprint.support.SpecCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SpecLoadJobHandler implements JobHandler {

    private static final String COLLECTED_MESSAGE = "명세 %d개 수집, 이번 스프린트 변경 화면 %d개, 반영 제안 %d건";

    private final SetupFacade setupFacade;

    private final SprintQueryService sprintQueryService;
    private final PrepareUnitService prepareUnitService;

    private final SetupStepRunner setupStepRunner;
    private final ConnectCollector connectCollector;
    private final SpecCollector specCollector;
    private final ReflectionProposer reflectionProposer;

    @Override
    public JobType type() {
        return JobType.SPEC_LOAD;
    }

    @Override
    public void handle(JobContext context) {
        if (context.sprintId() == null) {
            throw new NonRetryableJobException("명세 로드 작업에 스프린트가 없습니다. jobId=" + context.jobId());
        }
        SetupTargetDto target = sprintQueryService.getSetupTarget(context.sprintId());
        if (target.status() != SprintStatus.SPEC_LOAD) {
            throw new NonRetryableJobException("명세 로드 중인 스프린트가 아닙니다. sprintId=" + target.sprintId() + ", status=" + target.status());
        }

        setupStepRunner.run(context, target, SetupStepKey.CONNECT, () -> connect(context, target));
        SetupTargetDto connected = sprintQueryService.getSetupTarget(target.sprintId());
        setupStepRunner.run(context, connected, SetupStepKey.SPEC_COLLECT, () -> collectSpecs(context, connected));
    }

    private void connect(
            JobContext context,
            SetupTargetDto target
    ) {
        ConnectResultDto result = connectCollector.connect(context.jobId(), target);
        setupFacade.recordConnect(target.sprintId(), result);
        if (result.snapshot() != null) {
            setupFacade.saveSnapshots(target.sprintId(), context.jobId(), List.of(result.snapshot()));
        }
        if (result.isSuccess()) {
            return;
        }
        if (result.retryable()) {
            throw new IllegalStateException(result.failureMessage());
        }
        throw new NonRetryableJobException(result.failureMessage());
    }

    private void collectSpecs(
            JobContext context,
            SetupTargetDto target
    ) {
        long sprintId = target.sprintId();
        SpecCollectDto collected = specCollector.collect(context, target);
        Map<String, Long> screenIdToFeatureId = setupFacade.saveSpecs(sprintId, context.jobId(), target.sprintKey(), collected);

        Set<String> done = prepareUnitService.findDoneUnitKeys(sprintId, SetupStepKey.SPEC_COLLECT);
        int changed = 0;
        int proposals = 0;
        for (SpecPageDto page : collected.pages()) {
            if (page.isChanged(target.sprintKey())) {
                changed++;
            }
            List<NotionHistoryRowDto> rows = page.rowsToReflect(target.sprintKey());
            if (rows.isEmpty()) {
                continue;
            }
            proposals++;
            if (done.contains(page.screenId())) {
                continue;
            }
            context.checkCancelled();
            ReflectionProposalDto proposal = reflectionProposer.propose(page, rows, target.sprintKey());
            setupFacade.saveReflectionProposal(sprintId, screenIdToFeatureId.get(page.screenId()), page.screenId(), proposal);
        }

        setupFacade.finishSpecCollect(sprintId, COLLECTED_MESSAGE.formatted(collected.pages().size(), changed, proposals));
    }
}
