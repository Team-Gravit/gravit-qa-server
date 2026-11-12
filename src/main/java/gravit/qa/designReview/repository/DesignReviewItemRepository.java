package gravit.qa.designReview.repository;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.designReview.domain.DesignReviewItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DesignReviewItemRepository extends JpaRepository<DesignReviewItem, Long> {

    List<DesignReviewItem> findAllBySprintIdOrderByFeatureIdAscPlatformAscSortOrderAscIdAsc(long sprintId);

    List<DesignReviewItem> findAllBySprintId(long sprintId);

    long countBySprintIdAndState(
            long sprintId,
            CellState state
    );

    @Query("""
            SELECT COALESCE(MAX(i.sortOrder), -1)
            FROM DesignReviewItem i
            WHERE i.sprintId = :sprintId AND i.featureId = :featureId
    """)
    int findMaxSortOrder(
            @Param("sprintId") long sprintId,
            @Param("featureId") long featureId
    );
}
