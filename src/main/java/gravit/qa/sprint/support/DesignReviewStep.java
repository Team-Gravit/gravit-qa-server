package gravit.qa.sprint.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameDto;
import gravit.qa.adapter.figma.dto.internal.FigmaQaFramesDto;
import gravit.qa.adapter.figma.dto.internal.FigmaScreenDto;
import gravit.qa.adapter.figma.infrastructure.FigmaClient;
import gravit.qa.adapter.figma.support.FigmaOutlineRenderer;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.designReview.dto.internal.DesignReviewResultDto;
import gravit.qa.designReview.support.DesignReviewer;
import gravit.qa.feature.dto.internal.SprintFeatureDto;
import gravit.qa.feature.service.FeatureQueryService;
import gravit.qa.global.util.KstTimes;
import gravit.qa.job.support.JobContext;
import gravit.qa.job.support.NonRetryableJobException;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DesignReviewStep {

    private static final String JSON_FILE = "%s.json";
    private static final String NO_FIGMA_FILE = "Figma 파일 키가 없어 디자인 검수를 할 수 없습니다. 연결 단계부터 다시 확인하세요";
    private static final String DONE_MESSAGE = "화면 %d개 디자인 검수(이번 실행 %d개: 항목 %d개, 호출 %d회, 프레임 없음 %d개, 검수 실패 %d개, 입력 잘림 %d개)";

    private final SetupFacade setupFacade;

    private final FeatureQueryService featureQueryService;
    private final SprintQueryService sprintQueryService;
    private final PrepareUnitService prepareUnitService;

    private final DesignReviewer designReviewer;
    private final FigmaClient figmaClient;
    private final SnapshotFileStore snapshotFileStore;

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public void run(
            JobContext context,
            SetupTargetDto target
    ) {
        long sprintId = target.sprintId();
        String fileKey = sprintQueryService.getSprint(sprintId).figmaFileKey();
        if (fileKey == null) {
            throw new NonRetryableJobException(NO_FIGMA_FILE);
        }
        List<SprintFeatureDto> features = featureQueryService.getSprintFeatures(sprintId).stream()
                .filter(feature -> feature.feature().parts().stream().anyMatch(part -> DesignPlatform.WEB.getPart() == part || DesignPlatform.APP.getPart() == part))
                .toList();
        FigmaQaFramesDto qaFrames = figmaClient.getQaFrames(fileKey, target.sprintKey());

        Set<String> done = prepareUnitService.findDoneUnitKeys(sprintId, SetupStepKey.DESIGN_REVIEW);
        int screens = 0;
        int items = 0;
        int calls = 0;
        int frameMissing = 0;
        int failed = 0;
        int truncated = 0;
        for (SprintFeatureDto feature : features) {
            if (done.contains(feature.screenId())) {
                continue;
            }
            context.checkCancelled();

            Map<DesignPlatform, List<FigmaFrameDto>> platformToFrames = new EnumMap<>(DesignPlatform.class);
            List<String> nodeIds = new ArrayList<>();
            for (DesignPlatform platform : DesignPlatform.values()) {
                List<FigmaFrameDto> frames = qaFrames.findScreen(platform.getFigmaPlatform(), feature.screenId())
                        .map(FigmaScreenDto::frames)
                        .orElse(List.of());
                platformToFrames.put(platform, frames);
                frames.forEach(frame -> nodeIds.add(frame.nodeId()));
            }

            Map<String, String> nodeIdToOutline = new LinkedHashMap<>();
            SetupSnapshotDto snapshot = null;
            if (!nodeIds.isEmpty()) {
                Map<String, JsonNode> nodeIdToNode = figmaClient.getFrameNodes(fileKey, nodeIds);
                nodeIdToNode.forEach((nodeId, node) -> nodeIdToOutline.put(nodeId, FigmaOutlineRenderer.render(node)));
                snapshot = snapshot(sprintId, context.jobId(), feature.screenId(), nodeIdToOutline);
            }

            DesignReviewResultDto result = designReviewer.review(feature, platformToFrames, nodeIdToOutline);
            long missing = result.items().stream().filter(item -> DesignReviewer.FRAME_MISSING_REASON.equals(item.reason())).count();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("items", result.items().size());
            detail.put("calls", result.calls());
            detail.put("promptChars", result.promptChars());
            detail.put("truncated", result.truncated());
            detail.put("failed", result.failed());
            detail.put("frames", nodeIds.size());
            setupFacade.saveDesignReviewUnit(sprintId, feature.featureId(), feature.screenId(), result.items(), nodeIds, snapshot, context.jobId(), detail);

            screens++;
            items += result.items().size();
            calls += result.calls();
            frameMissing += (int) missing;
            failed += result.failed() ? 1 : 0;
            truncated += result.truncated() ? 1 : 0;
        }

        String message = DONE_MESSAGE.formatted(features.size(), screens, items, calls, frameMissing, failed, truncated);
        setupFacade.finishQaStep(sprintId, SetupStepKey.DESIGN_REVIEW, message);
    }

    private SetupSnapshotDto snapshot(
            long sprintId,
            long jobId,
            String screenId,
            Map<String, String> nodeIdToOutline
    ) {
        try {
            String content = objectMapper.writeValueAsString(nodeIdToOutline);
            return SetupSnapshotDto.of(
                    SnapshotSource.FIGMA_NODES,
                    screenId,
                    screenId,
                    snapshotFileStore.save(sprintId, jobId, SnapshotSource.FIGMA_NODES, JSON_FILE.formatted(screenId), content),
                    KstTimes.now(clock)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Figma 레이어 스냅샷을 만들 수 없습니다: " + screenId, e);
        }
    }
}
