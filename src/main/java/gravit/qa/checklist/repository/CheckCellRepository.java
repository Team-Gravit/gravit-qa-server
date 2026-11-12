package gravit.qa.checklist.repository;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.CheckCell;
import gravit.qa.checklist.domain.CheckCellId;
import gravit.qa.checklist.dto.internal.CellStateCountDto;
import gravit.qa.checklist.dto.internal.ClassificationCountDto;
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
            SELECT new gravit.qa.checklist.dto.internal.CellStateCountDto(c.part, c.state, COUNT(c))
            FROM CheckCell c JOIN ChecklistItem i ON i.id = c.itemId
            WHERE i.sprintId = :sprintId
            GROUP BY c.part, c.state
    """)
    List<CellStateCountDto> countStatesBySprintId(@Param("sprintId") long sprintId);

    @Query("""
            SELECT COUNT(c)
            FROM CheckCell c JOIN ChecklistItem i ON i.id = c.itemId
            WHERE i.sprintId = :sprintId AND c.state = :state
    """)
    long countBySprintIdAndState(
            @Param("sprintId") long sprintId,
            @Param("state") CellState state
    );

    @Query("""
            SELECT new gravit.qa.checklist.dto.internal.ClassificationCountDto(i.classification, COUNT(c))
            FROM CheckCell c JOIN ChecklistItem i ON i.id = c.itemId
            WHERE i.sprintId = :sprintId AND c.state = :state
            GROUP BY i.classification
    """)
    List<ClassificationCountDto> countClassificationsBySprintIdAndState(
            @Param("sprintId") long sprintId,
            @Param("state") CellState state
    );

    @Query("""
            SELECT c
            FROM CheckCell c JOIN ChecklistItem i ON i.id = c.itemId
            WHERE i.sprintId = :sprintId AND c.part = :part AND c.state = :state
    """)
    List<CheckCell> findAllBySprintIdAndPartAndState(
            @Param("sprintId") long sprintId,
            @Param("part") Part part,
            @Param("state") CellState state
    );
}
