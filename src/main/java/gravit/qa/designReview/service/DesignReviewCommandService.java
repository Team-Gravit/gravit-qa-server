package gravit.qa.designReview.service;

import gravit.qa.designReview.domain.DesignReviewItem;
import gravit.qa.designReview.dto.internal.DesignReviewItemDraftDto;
import gravit.qa.designReview.dto.request.DesignReviewItemUpdateRequest;
import gravit.qa.designReview.dto.request.DesignReviewStateUpdateRequest;
import gravit.qa.designReview.dto.response.DesignReviewItemResponse;
import gravit.qa.designReview.repository.DesignReviewItemHistoryRepository;
import gravit.qa.designReview.repository.DesignReviewItemRepository;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.global.util.KstTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static gravit.qa.global.exception.domain.CustomErrorCode.DESIGN_REVIEW_ITEM_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class DesignReviewCommandService {

    public static final String EDIT_RESET_REASON = "항목 수정으로 판정 초기화";

    private final DesignReviewItemRepository designReviewItemRepository;
    private final DesignReviewItemHistoryRepository designReviewItemHistoryRepository;

    private final Clock clock;

    @Transactional
    public void saveJudged(
            long sprintId,
            long featureId,
            List<DesignReviewItemDraftDto> drafts
    ) {
        LocalDateTime now = KstTimes.now(clock);
        int sortOrder = designReviewItemRepository.findMaxSortOrder(sprintId, featureId) + 1;
        for (DesignReviewItemDraftDto draft : drafts) {
            DesignReviewItem item = designReviewItemRepository.save(DesignReviewItem.judge(sprintId, featureId, draft, sortOrder++));
            designReviewItemHistoryRepository.save(item.initialHistory(now));
        }
    }

    @Transactional
    public DesignReviewItemResponse updateState(
            long itemId,
            DesignReviewStateUpdateRequest request,
            long adminId
    ) {
        DesignReviewItem item = findItem(itemId);
        item.requireVersion(request.version());

        if (item.getState() != request.state()) {
            designReviewItemHistoryRepository.save(item.change(request.state(), adminId, KstTimes.now(clock)));
            designReviewItemRepository.flush();
        }
        return DesignReviewItemResponse.of(item, true);
    }

    @Transactional
    public DesignReviewItemResponse updateItem(
            long itemId,
            DesignReviewItemUpdateRequest request,
            long adminId
    ) {
        DesignReviewItem item = findItem(itemId);
        item.requireVersion(request.version());

        boolean contentChanged = false;
        if (request.title() != null && !request.title().trim().equals(item.getTitle())) {
            item.changeTitle(request.title().trim());
            contentChanged = true;
        }
        if (request.expected() != null && !request.expected().trim().equals(item.getExpected())) {
            item.changeExpected(request.expected().trim());
            contentChanged = true;
        }

        if (contentChanged) {
            item.resetJudgment(EDIT_RESET_REASON, adminId, KstTimes.now(clock)).ifPresent(designReviewItemHistoryRepository::save);
            designReviewItemRepository.flush();
        }
        return DesignReviewItemResponse.of(item, true);
    }

    @Transactional
    public void deleteItem(
            long itemId,
            int version
    ) {
        DesignReviewItem item = findItem(itemId);
        item.requireVersion(version);

        designReviewItemRepository.delete(item);
        designReviewItemRepository.flush();
    }

    private DesignReviewItem findItem(long itemId) {
        return designReviewItemRepository.findById(itemId)
                .orElseThrow(() -> new RestApiException(DESIGN_REVIEW_ITEM_NOT_FOUND));
    }
}
