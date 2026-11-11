package gravit.qa.sprint.support;

import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.autoReview.dto.internal.AutoReviewJudgmentDto;
import gravit.qa.autoReview.dto.internal.AutoReviewResultDto;
import gravit.qa.autoReview.dto.internal.CodeSelectionDto;
import gravit.qa.autoReview.support.AutoReviewer;
import gravit.qa.autoReview.support.CodeSelector;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.CellJudgmentDto;
import gravit.qa.checklist.dto.internal.ChecklistItemDto;
import gravit.qa.checklist.service.ChecklistQueryService;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.dto.internal.SprintFeatureDto;
import gravit.qa.feature.service.FeatureQueryService;
import gravit.qa.job.support.JobContext;
import gravit.qa.job.support.NonRetryableJobException;
import gravit.qa.sprint.domain.ConnectionTarget;
import gravit.qa.sprint.domain.SetupStepKey;
import gravit.qa.sprint.dto.internal.ConnectionResultDto;
import gravit.qa.sprint.dto.internal.SetupTargetDto;
import gravit.qa.sprint.facade.SetupFacade;
import gravit.qa.sprint.service.PrepareUnitService;
import gravit.qa.sprint.service.SprintQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AutoReviewStep {

    private static final String UNIT_KEY = "%s:%s";
    private static final String NO_COMMIT = "연결 단계의 %s 저장소 커밋이 없습니다. 준비를 다시 시작하세요";
    private static final String DONE_MESSAGE = "단위 %d개 1차 판정(이번 실행 %d개: 호출 %d회, PASS %d, FAIL %d, 판정 불가 %d, 코드 없음 %d개, 빠진 파일 %d개)";

    private final SetupFacade setupFacade;

    private final ChecklistQueryService checklistQueryService;
    private final FeatureQueryService featureQueryService;
    private final SprintQueryService sprintQueryService;
    private final PrepareUnitService prepareUnitService;

    private final CodeSelector codeSelector;
    private final AutoReviewer autoReviewer;

    public void run(
            JobContext context,
            SetupTargetDto target
    ) {
        long sprintId = target.sprintId();
        Map<GithubRepo, String> repoToCommitSha = commits(sprintId);
        Map<Long, SprintFeatureDto> featureIdToFeature = featureQueryService.getSprintFeatures(sprintId).stream()
                .collect(Collectors.toMap(SprintFeatureDto::featureId, Function.identity()));

        Map<String, List<ChecklistItemDto>> unitToItems = new LinkedHashMap<>();
        Map<String, SprintFeatureDto> unitToFeature = new LinkedHashMap<>();
        Map<String, Part> unitToPart = new LinkedHashMap<>();
        checklistQueryService.getItems(sprintId, Classification.AUTO).stream()
                .filter(item -> featureIdToFeature.containsKey(item.featureId()))
                .sorted(Comparator.comparing((ChecklistItemDto item) -> featureIdToFeature.get(item.featureId()).screenId()).thenComparing(ChecklistItemDto::id))
                .forEach(item -> item.parts().forEach(part -> {
                    SprintFeatureDto feature = featureIdToFeature.get(item.featureId());
                    String unitKey = UNIT_KEY.formatted(feature.screenId(), part);
                    unitToItems.computeIfAbsent(unitKey, key -> new ArrayList<>()).add(item);
                    unitToFeature.put(unitKey, feature);
                    unitToPart.put(unitKey, part);
                }));

        Set<String> done = prepareUnitService.findDoneUnitKeys(sprintId, SetupStepKey.AUTO_REVIEW);
        int units = 0;
        int calls = 0;
        int pass = 0;
        int fail = 0;
        int unknown = 0;
        int noCode = 0;
        int dropped = 0;
        for (Map.Entry<String, List<ChecklistItemDto>> entry : unitToItems.entrySet()) {
            String unitKey = entry.getKey();
            if (done.contains(unitKey)) {
                continue;
            }
            context.checkCancelled();
            SprintFeatureDto feature = unitToFeature.get(unitKey);
            Part part = unitToPart.get(unitKey);

            CodeSelectionDto selection = codeSelector.select(part, feature.feature(), entry.getValue(), repoToCommitSha);
            AutoReviewResultDto result = autoReviewer.review(feature, part, entry.getValue(), selection, context);

            List<CellJudgmentDto> judgments = result.judgments().stream()
                    .map(judgment -> CellJudgmentDto.of(judgment.itemId(), part, judgment.verdict().toCellState(), judgment.reason()))
                    .toList();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("items", entry.getValue().size());
            detail.put("files", result.files());
            detail.put("calls", result.calls());
            detail.put("promptChars", result.promptChars());
            detail.put("droppedFiles", result.droppedFiles());
            detail.put("commits", selection.repoToCommitSha());
            detail.put("failed", result.failed());
            setupFacade.saveAutoReviewUnit(sprintId, unitKey, judgments, detail);

            units++;
            calls += result.calls();
            noCode += selection.isEmpty() ? 1 : 0;
            dropped += result.droppedFiles().size();
            for (AutoReviewJudgmentDto judgment : result.judgments()) {
                switch (judgment.verdict()) {
                    case PASS -> pass++;
                    case FAIL -> fail++;
                    case UNKNOWN -> unknown++;
                }
            }
        }

        String message = DONE_MESSAGE.formatted(unitToItems.size(), units, calls, pass, fail, unknown, noCode, dropped);
        setupFacade.finishQaStep(sprintId, SetupStepKey.AUTO_REVIEW, message);
    }

    private Map<GithubRepo, String> commits(long sprintId) {
        Map<GithubRepo, String> repoToCommitSha = new EnumMap<>(GithubRepo.class);
        for (Map.Entry<ConnectionTarget, GithubRepo> entry : ConnectCollector.TARGET_TO_REPO.entrySet()) {
            String commitSha = sprintQueryService.findConnection(sprintId, entry.getKey())
                    .filter(ConnectionResultDto::connected)
                    .map(ConnectionResultDto::commitSha)
                    .orElseThrow(() -> new NonRetryableJobException(NO_COMMIT.formatted(entry.getValue().getRepoName())));
            repoToCommitSha.put(entry.getValue(), commitSha);
        }
        return repoToCommitSha;
    }
}
