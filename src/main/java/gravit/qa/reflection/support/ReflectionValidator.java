package gravit.qa.reflection.support;

import gravit.qa.adapter.notion.support.NotionWriteGuard;
import gravit.qa.reflection.domain.ReflectionOperationType;
import gravit.qa.reflection.dto.internal.ReflectionOperationOutputDto;
import gravit.qa.reflection.dto.internal.ReflectionOutputDto;
import gravit.qa.reflection.dto.internal.ReflectionSkippedRowOutputDto;
import gravit.qa.reflection.dto.internal.SpecBlockEntryDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReflectionValidator {

    private static final String OPERATION_PATH = "operations[%d]";
    private static final String DELIMITER = ", ";

    public static List<String> validate(
            ReflectionOutputDto output,
            SpecBlockIndex index,
            int historyRowCount
    ) {
        List<String> errors = new ArrayList<>();
        Set<Integer> coveredRows = new HashSet<>();
        Set<String> changedTargets = new HashSet<>();

        List<ReflectionOperationOutputDto> operations = output.operations() == null ? List.of() : output.operations();
        for (int position = 0; position < operations.size(); position++) {
            ReflectionOperationOutputDto operation = operations.get(position);
            String path = OPERATION_PATH.formatted(position);
            if (!isHistoryRow(operation.historyRow(), historyRowCount)) {
                errors.add(path + ".historyRow " + operation.historyRow() + "는 1부터 " + historyRowCount + " 사이여야 합니다.");
            } else {
                coveredRows.add(operation.historyRow());
            }

            Optional<SpecBlockEntryDto> target = index.find(operation.target());
            if (target.isEmpty()) {
                errors.add(path + ".target '" + operation.target() + "'는 본문 블록 목록에 없는 키입니다.");
                continue;
            }
            validateOperation(operation, target.get(), path, errors);
            if ((operation.op() == ReflectionOperationType.UPDATE_TEXT
                    || operation.op() == ReflectionOperationType.UPDATE_ROW
                    || operation.op() == ReflectionOperationType.DELETE)
                    && !changedTargets.add(target.get().key())) {
                errors.add(path + ".target '" + operation.target() + "'를 두 번 이상 바꾸거나 지웁니다. 한 편집으로 합치세요.");
            }
        }

        List<ReflectionSkippedRowOutputDto> skippedRows = output.skippedRows() == null ? List.of() : output.skippedRows();
        for (ReflectionSkippedRowOutputDto skipped : skippedRows) {
            if (!isHistoryRow(skipped.historyRow(), historyRowCount)) {
                errors.add("skippedRows.historyRow " + skipped.historyRow() + "는 1부터 " + historyRowCount + " 사이여야 합니다.");
                continue;
            }
            if (skipped.reason() == null || skipped.reason().isBlank()) {
                errors.add("skippedRows의 " + skipped.historyRow() + "번 행 reason이 비어 있습니다.");
            }
            coveredRows.add(skipped.historyRow());
        }

        Set<Integer> missing = new TreeSet<>(IntStream.rangeClosed(1, historyRowCount).boxed().toList());
        missing.removeAll(coveredRows);
        if (!missing.isEmpty()) {
            errors.add("편집이나 skippedRows로 다루지 않은 수정 이력 행이 있습니다: " + String.join(DELIMITER, missing.stream().map(String::valueOf).toList()));
        }
        return errors;
    }

    private static void validateOperation(
            ReflectionOperationOutputDto operation,
            SpecBlockEntryDto target,
            String path,
            List<String> errors
    ) {
        switch (operation.op()) {
            case UPDATE_TEXT -> {
                if (target.isRow() || target.isTable() || !target.editable()) {
                    errors.add(path + ": UPDATE_TEXT는 수정할 수 있는 글 블록에만 씁니다. 표 행은 UPDATE_ROW를 씁니다.");
                }
                requireText(operation.text(), path, errors);
            }
            case UPDATE_ROW -> {
                if (!target.isRow() || target.headerRow()) {
                    errors.add(path + ": UPDATE_ROW는 머리글이 아닌 표 행에만 씁니다.");
                    return;
                }
                requireCells(operation.cells(), target.tableWidth(), path, errors);
                if (target.basicTable() && operation.cells() != null && !operation.cells().isEmpty()
                        && !operation.cells().getFirst().trim().equals(target.cells().getFirst().trim())) {
                    errors.add(path + ": 명세 맨 위 항목, 값 표의 첫 칸(항목 이름)은 바꿀 수 없습니다.");
                }
            }
            case INSERT_BLOCK_AFTER -> {
                if (target.isRow()) {
                    errors.add(path + ": 표 행 뒤에는 INSERT_ROW_AFTER로 행을 추가합니다.");
                }
                if (operation.blockType() == null || !NotionWriteGuard.TEXT_BLOCK_TYPES.contains(operation.blockType())) {
                    errors.add(path + ".blockType '" + operation.blockType() + "'는 추가할 수 있는 블록 종류가 아닙니다.");
                }
                requireText(operation.text(), path, errors);
            }
            case INSERT_ROW_AFTER -> {
                if (!target.isRow()) {
                    errors.add(path + ": INSERT_ROW_AFTER는 표 행 키를 대상으로 합니다.");
                    return;
                }
                if (target.basicTable()) {
                    errors.add(path + ": 명세 맨 위 항목, 값 표에는 행을 추가할 수 없습니다.");
                }
                requireCells(operation.cells(), target.tableWidth(), path, errors);
            }
            case DELETE -> {
                if (target.isTable() || !target.editable() || (target.isRow() && target.basicTable())) {
                    errors.add(path + ": DELETE는 글 블록이나 머리글이 아닌 표 행에만 씁니다(맨 위 항목, 값 표 제외).");
                }
            }
        }
    }

    private static boolean isHistoryRow(
            int historyRow,
            int historyRowCount
    ) {
        return historyRow >= 1 && historyRow <= historyRowCount;
    }

    private static void requireText(
            String text,
            String path,
            List<String> errors
    ) {
        if (text == null || text.isBlank()) {
            errors.add(path + ".text가 비어 있습니다.");
        }
    }

    private static void requireCells(
            List<String> cells,
            Integer width,
            String path,
            List<String> errors
    ) {
        if (cells == null || width == null || cells.size() != width) {
            errors.add(path + ".cells는 그 표의 열 수(" + width + "개)만큼 있어야 합니다.");
        }
    }
}
