package gravit.qa.checklist.repository;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.checklist.domain.CheckCellId;
import gravit.qa.checklist.domain.ReviewState;
import gravit.qa.checklist.dto.internal.CellStateCountDto;
import gravit.qa.feature.domain.Part;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CheckCellRepository extends JpaRepository<CheckCell, CheckCellId> {

    List<CheckCell> findAllByItemIdIn(Collection<Long> itemIds);

    List<CheckCell> findAllByItemId(long itemId);

    Optional<CheckCell> findByItemIdAndPart(
            long itemId,
            Part part
    );

    @Query("""
            SELECT new gravit.qa.checklist.dto.internal.CellStateCountDto(i.sprintId, c.part, c.state, COUNT(c))
            FROM CheckCell c JOIN ChecklistItem i ON i.id = c.itemId
            WHERE i.sprintId IN :sprintIds AND i.reviewState <> :rejected
            GROUP BY i.sprintId, c.part, c.state
    """)
    List<CellStateCountDto> countStatesBySprintIds(
            @Param("sprintIds") Collection<Long> sprintIds,
            @Param("rejected") ReviewState rejected
    );

    @Query("""
            SELECT COUNT(c)
            FROM CheckCell c JOIN ChecklistItem i ON i.id = c.itemId
            WHERE i.sprintId = :sprintId AND i.reviewState <> :rejected AND c.state = :state
    """)
    long countBySprintIdAndState(
            @Param("sprintId") long sprintId,
            @Param("rejected") ReviewState rejected,
            @Param("state") CellState state
    );
}
