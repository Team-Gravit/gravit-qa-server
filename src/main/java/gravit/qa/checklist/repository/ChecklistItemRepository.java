package gravit.qa.checklist.repository;

import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.domain.ReviewState;
import gravit.qa.checklist.dto.internal.SprintItemCountDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findAllBySprintIdOrderByFeatureIdAscSortOrderAscIdAsc(long sprintId);

    long countBySprintIdAndReviewState(
            long sprintId,
            ReviewState reviewState
    );

    boolean existsBySprintIdAndClassificationAndReviewState(
            long sprintId,
            Classification classification,
            ReviewState reviewState
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM ChecklistItem i
            WHERE i.sprintId = :sprintId AND i.reviewState = :proposed
    """)
    void deleteProposed(
            @Param("sprintId") long sprintId,
            @Param("proposed") ReviewState proposed
    );

    @Query("""
            SELECT COALESCE(MAX(i.sortOrder), -1)
            FROM ChecklistItem i
            WHERE i.sprintId = :sprintId AND i.featureId = :featureId
    """)
    int findMaxSortOrder(
            @Param("sprintId") long sprintId,
            @Param("featureId") long featureId
    );

    @Query("""
            SELECT new gravit.qa.checklist.dto.internal.SprintItemCountDto(i.sprintId, COUNT(i))
            FROM ChecklistItem i
            WHERE i.sprintId IN :sprintIds AND i.reviewState = :reviewState
            GROUP BY i.sprintId
    """)
    List<SprintItemCountDto> countBySprintIdsAndReviewState(
            @Param("sprintIds") Collection<Long> sprintIds,
            @Param("reviewState") ReviewState reviewState
    );
}
