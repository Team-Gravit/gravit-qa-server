package gravit.qa.designReview.repository;

import gravit.qa.designReview.domain.DesignReviewItemHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DesignReviewItemHistoryRepository extends JpaRepository<DesignReviewItemHistory, Long> {

    List<DesignReviewItemHistory> findAllByItemIdOrderByChangedAtDescIdDesc(long itemId);
}
