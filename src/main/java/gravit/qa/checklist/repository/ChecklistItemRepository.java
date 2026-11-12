package gravit.qa.checklist.repository;

import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    List<ChecklistItem> findAllBySprintIdOrderByFeatureIdAscSortOrderAscIdAsc(long sprintId);

    List<ChecklistItem> findAllBySprintIdAndClassificationOrderByFeatureIdAscSortOrderAscIdAsc(
            long sprintId,
            Classification classification
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
}
