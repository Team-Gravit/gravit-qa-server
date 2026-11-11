package gravit.qa.sprint.fixture;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.ChecklistItemDraftDto;
import gravit.qa.checklist.repository.CheckCellRepository;
import gravit.qa.checklist.repository.ChecklistItemRepository;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.designReview.domain.DesignReviewItem;
import gravit.qa.designReview.dto.internal.DesignReviewItemDraftDto;
import gravit.qa.designReview.repository.DesignReviewItemRepository;
import gravit.qa.feature.domain.Feature;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecSection;
import gravit.qa.feature.domain.SpecStatus;
import gravit.qa.feature.domain.SprintFeature;
import gravit.qa.feature.dto.internal.FeatureUpsertDto;
import gravit.qa.feature.dto.internal.SpecRowDto;
import gravit.qa.feature.dto.internal.SprintFeatureSpecDto;
import gravit.qa.feature.repository.FeatureRepository;
import gravit.qa.feature.repository.SprintFeatureRepository;
import gravit.qa.global.util.TimeZoneConst;
import gravit.qa.reflection.domain.SpecReflection;
import gravit.qa.reflection.dto.internal.ReflectionHistoryRowDto;
import gravit.qa.reflection.dto.internal.ReflectionProposalDto;
import gravit.qa.reflection.repository.SpecReflectionRepository;
import gravit.qa.sprint.domain.ConnectionTarget;
import gravit.qa.sprint.domain.SetupStepKey;
import gravit.qa.sprint.domain.SetupStepStatus;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintConnection;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.domain.SprintStep;
import gravit.qa.sprint.dto.internal.ConnectionResultDto;
import gravit.qa.sprint.repository.SprintConnectionRepository;
import gravit.qa.sprint.repository.SprintRepository;
import gravit.qa.sprint.repository.SprintStepRepository;
import gravit.qa.support.TestClockConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@TestComponent
@RequiredArgsConstructor
public class SprintFixture {

    public static final long ADMIN_ID = 7L;
    public static final List<String> SCREEN_COLUMNS = List.of("#", "요소", "내용", "언제", "근거");

    private final SprintRepository sprintRepository;
    private final SprintConnectionRepository sprintConnectionRepository;
    private final SprintStepRepository sprintStepRepository;
    private final FeatureRepository featureRepository;
    private final SprintFeatureRepository sprintFeatureRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final CheckCellRepository checkCellRepository;
    private final DesignReviewItemRepository designReviewItemRepository;
    private final SpecReflectionRepository specReflectionRepository;

    public Sprint 스프린트(SprintStatus status) {
        return 스프린트(SetupStubFixture.SPRINT_KEY, SetupStubFixture.SPRINT_PAGE_ID, status);
    }

    public Sprint 스프린트(
            String sprintKey,
            String notionPageId,
            SprintStatus status
    ) {
        Sprint sprint = Sprint.create(sprintKey, notionPageId, "유닛 상세 개편", SetupStubFixture.FILE_KEY, ADMIN_ID);
        ReflectionTestUtils.setField(sprint, "status", status);
        if (status == SprintStatus.CLOSED) {
            ReflectionTestUtils.setField(sprint, "closedAt", 기준_시각().minusDays(1));
        }
        return sprintRepository.save(sprint);
    }

    public void 연결_성공(long sprintId) {
        sprintConnectionRepository.saveAll(List.of(
                SprintConnection.create(sprintId, ConnectionResultDto.connected(ConnectionTarget.NOTION), 기준_시각()),
                SprintConnection.create(sprintId, ConnectionResultDto.connected(ConnectionTarget.FIGMA), 기준_시각()),
                SprintConnection.create(sprintId, ConnectionResultDto.connected(ConnectionTarget.SERVER, "qa-baseline", SetupStubFixture.SERVER_SHA), 기준_시각()),
                SprintConnection.create(sprintId, ConnectionResultDto.connected(ConnectionTarget.WEB, "qa-baseline", SetupStubFixture.WEB_SHA), 기준_시각()),
                SprintConnection.create(sprintId, ConnectionResultDto.connected(ConnectionTarget.APP, "qa-baseline", SetupStubFixture.AOS_SHA), 기준_시각())
        ));
    }

    public SprintStep 단계(
            long sprintId,
            SetupStepKey key,
            SetupStepStatus status
    ) {
        SprintStep step = SprintStep.create(sprintId, key);
        step.start(기준_시각());
        step.finish(status, null, 기준_시각());
        return sprintStepRepository.save(step);
    }

    public Feature 기능(
            String screenId,
            List<Part> parts
    ) {
        String notionPageId = UUID.randomUUID().toString().replace("-", "");
        return featureRepository.save(Feature.create(FeatureUpsertDto.of(screenId, screenId + " 화면", "/learning", parts, notionPageId, SpecStatus.CONFIRMED, List.of())));
    }

    public SprintFeature 대상_기능(
            long sprintId,
            long featureId,
            boolean changed
    ) {
        return 대상_기능(sprintId, featureId, changed, List.of(화면_행("C1", "로그인 버튼", "항상")));
    }

    public SprintFeature 대상_기능(
            long sprintId,
            long featureId,
            boolean changed,
            List<SpecRowDto> rows
    ) {
        return sprintFeatureRepository.save(SprintFeature.create(sprintId, SprintFeatureSpecDto.of(featureId, changed, "### 화면에 보이는 것", rows, List.of())));
    }

    public static SpecRowDto 화면_행(
            String rowId,
            String element,
            String when
    ) {
        return SpecRowDto.of(rowId, SpecSection.SCREEN, SCREEN_COLUMNS, List.of(rowId, element, element + " 내용", when, "🎨"));
    }

    public ChecklistItem 항목(
            long sprintId,
            long featureId,
            Classification classification,
            List<Part> parts
    ) {
        ChecklistItem item = ChecklistItem.generate(
                sprintId,
                featureId,
                ChecklistItemDraftDto.of("항목 " + classification, "설명", parts, classification, List.of("C1")),
                List.of(),
                0
        );
        ChecklistItem saved = checklistItemRepository.save(item);
        checkCellRepository.saveAll(CheckCell.initialize(saved.getId(), saved.getParts()));
        return saved;
    }

    public CheckCell 셀_체크(
            long itemId,
            Part part,
            CellState state
    ) {
        CheckCell cell = checkCellRepository.findByItemIdAndPart(itemId, part).orElseThrow();
        cell.check(state, ADMIN_ID, 기준_시각());
        return checkCellRepository.save(cell);
    }

    public CheckCell 셀_판정(
            long itemId,
            Part part,
            CellState state,
            String reason
    ) {
        CheckCell cell = checkCellRepository.findByItemIdAndPart(itemId, part).orElseThrow();
        cell.judge(state, reason, 기준_시각());
        return checkCellRepository.save(cell);
    }

    public DesignReviewItem 디자인_항목(
            long sprintId,
            long featureId,
            DesignPlatform platform,
            CellState state
    ) {
        return designReviewItemRepository.save(DesignReviewItem.judge(
                sprintId,
                featureId,
                DesignReviewItemDraftDto.of(platform, "로그인 버튼 영역 시안이 명세와 같다", "버튼 순서를 확인한다", List.of("C1"), List.of("4:2"), state, "자동 비교"),
                0
        ));
    }

    public SpecReflection 반영_제안(
            long sprintId,
            long featureId
    ) {
        ReflectionProposalDto proposal = ReflectionProposalDto.proposed(
                List.of(new ReflectionHistoryRowDto("77777777777777777777777777777777", SetupStubFixture.SPRINT_KEY, "버튼 문구 변경", List.of(SetupStubFixture.SPRINT_KEY, "버튼 문구 변경", ""))),
                List.of(),
                Map.of(),
                "before",
                "after",
                10
        );
        return specReflectionRepository.save(SpecReflection.create(sprintId, featureId, proposal));
    }

    private LocalDateTime 기준_시각() {
        return LocalDateTime.ofInstant(TestClockConfig.BASE_INSTANT, TimeZoneConst.KST);
    }
}
