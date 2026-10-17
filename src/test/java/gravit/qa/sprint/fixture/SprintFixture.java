package gravit.qa.sprint.fixture;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.domain.ReviewState;
import gravit.qa.checklist.dto.internal.ChecklistItemDraftDto;
import gravit.qa.checklist.fixture.TestCatalogFixture;
import gravit.qa.checklist.repository.CheckCellRepository;
import gravit.qa.checklist.repository.ChecklistItemRepository;
import gravit.qa.feature.domain.Feature;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecStatus;
import gravit.qa.feature.domain.SprintFeature;
import gravit.qa.feature.dto.internal.FeatureUpsertDto;
import gravit.qa.feature.repository.FeatureRepository;
import gravit.qa.feature.repository.SprintFeatureRepository;
import gravit.qa.global.util.TimeZoneConst;
import gravit.qa.sprint.domain.Baseline;
import gravit.qa.sprint.domain.BaselineRepo;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.SetupBaselineRepoDto;
import gravit.qa.sprint.dto.internal.WarningDto;
import gravit.qa.sprint.repository.BaselineRepoRepository;
import gravit.qa.sprint.repository.BaselineRepository;
import gravit.qa.sprint.repository.SprintRepository;
import gravit.qa.support.TestClockConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@TestComponent
@RequiredArgsConstructor
public class SprintFixture {

    public static final long ADMIN_ID = 7L;

    private final SprintRepository sprintRepository;
    private final BaselineRepository baselineRepository;
    private final BaselineRepoRepository baselineRepoRepository;
    private final FeatureRepository featureRepository;
    private final SprintFeatureRepository sprintFeatureRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final CheckCellRepository checkCellRepository;

    public Sprint 스프린트(SprintStatus status) {
        return 스프린트(SetupStubFixture.SPRINT_KEY, SetupStubFixture.SPRINT_PAGE_ID, status);
    }

    public Sprint 스프린트(
            String sprintKey,
            String notionPageId,
            SprintStatus status
    ) {
        Sprint sprint = Sprint.create(sprintKey, notionPageId, null, null, SetupStubFixture.FILE_KEY, ADMIN_ID);
        ReflectionTestUtils.setField(sprint, "status", status);
        if (status == SprintStatus.CLOSED) {
            ReflectionTestUtils.setField(sprint, "closedAt", 기준_시각().minusDays(1));
        }
        return sprintRepository.save(sprint);
    }

    public Baseline 기준점(
            long sprintId,
            String serverSha,
            String webSha,
            String aosSha,
            int aosVersionCode
    ) {
        Baseline baseline = baselineRepository.save(Baseline.create(sprintId, null, 기준_시각()));
        baselineRepoRepository.saveAll(List.of(
                BaselineRepo.create(baseline.getId(), SetupBaselineRepoDto.of("gravit-server", BaselineRepo.RELEASE_BRANCH, serverSha, null, null, null, false)),
                BaselineRepo.create(baseline.getId(), SetupBaselineRepoDto.of("gravit-web", BaselineRepo.RELEASE_BRANCH, webSha, null, null, null, false)),
                BaselineRepo.create(baseline.getId(), SetupBaselineRepoDto.of("gravit-aos", BaselineRepo.RELEASE_BRANCH, aosSha, null, aosVersionCode, "1.0." + aosVersionCode, false))
        ));
        return baseline;
    }

    public Baseline 기준점(long sprintId) {
        return 기준점(sprintId, SetupStubFixture.SERVER_SHA, SetupStubFixture.WEB_SHA, SetupStubFixture.AOS_SHA, 1);
    }

    public Feature 기능(
            String screenId,
            List<Part> parts
    ) {
        String notionPageId = UUID.randomUUID().toString().replace("-", "");
        return featureRepository.save(Feature.create(FeatureUpsertDto.of(screenId, screenId + " 화면", null, parts, notionPageId, SpecStatus.CONFIRMED, List.of())));
    }

    public SprintFeature 대상_기능(
            long sprintId,
            long featureId,
            List<WarningDto> warnings
    ) {
        return sprintFeatureRepository.save(SprintFeature.create(sprintId, featureId, List.of("4:2"), warnings));
    }

    public ChecklistItem 항목(
            long sprintId,
            long featureId,
            Classification classification,
            ReviewState reviewState,
            List<Part> parts
    ) {
        String testRef = classification == Classification.AUTO ? TestCatalogFixture.LRN_EMPTY_STATE_TEST : null;
        ChecklistItem item = reviewState == ReviewState.PROPOSED
                ? ChecklistItem.propose(sprintId, featureId, ChecklistItemDraftDto.of("제안 항목", "기대 결과", parts, classification, null, false, testRef, null), List.of(), 0)
                : ChecklistItem.createByHuman(sprintId, featureId, "확정 항목", "기대 결과", parts, classification, testRef, null, 0);
        if (reviewState == ReviewState.REJECTED) {
            item.review(ReviewState.REJECTED);
        }

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

    private LocalDateTime 기준_시각() {
        return LocalDateTime.ofInstant(TestClockConfig.BASE_INSTANT, TimeZoneConst.KST);
    }
}
