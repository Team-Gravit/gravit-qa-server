package gravit.qa.checklist.repository;

import gravit.qa.checklist.domain.CheckCellHistory;
import gravit.qa.feature.domain.Part;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CheckCellHistoryRepository extends JpaRepository<CheckCellHistory, Long> {

    List<CheckCellHistory> findAllByItemIdAndPartOrderByChangedAtDescIdDesc(
            long itemId,
            Part part
    );

    List<CheckCellHistory> findAllByItemIdInAndPartOrderByChangedAtDescIdDesc(
            Collection<Long> itemIds,
            Part part
    );
}
