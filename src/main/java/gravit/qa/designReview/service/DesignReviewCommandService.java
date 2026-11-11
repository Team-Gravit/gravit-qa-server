package gravit.qa.designReview.service;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.designReview.domain.DesignReviewItem;
import gravit.qa.designReview.dto.internal.DesignReviewItemDraftDto;
import gravit.qa.designReview.dto.request.DesignReviewItemUpdateRequest;
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
import static gravit.qa.global.exception.domain.CustomErrorCode.DESIGN_REVIEW_STATE_INVALID;

@Service
@RequiredArgsConstructor
public class DesignReviewCommandService {

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
    public DesignReviewItemResponse update(
            long itemId,
            DesignReviewItemUpdateRequest request,
            long adminId,
            boolean editable
    ) {
        if (request.state() == CellState.NOT_APPLICABLE) {
            throw new RestApiException(DESIGN_REVIEW_STATE_INVALID);
        }

        DesignReviewItem item = designReviewItemRepository.findById(itemId)
                .orElseThrow(() -> new RestApiException(DESIGN_REVIEW_ITEM_NOT_FOUND));
        item.requireVersion(request.version());

        if (item.getState() != request.state()) {
            designReviewItemHistoryRepository.save(item.change(request.state(), adminId, KstTimes.now(clock)));
            designReviewItemRepository.flush();
        }
        return DesignReviewItemResponse.of(item, editable);
    }
}
