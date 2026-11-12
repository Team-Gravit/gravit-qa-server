package gravit.qa.checklist.support;

import gravit.qa.checklist.dto.internal.ChecklistDraftItemOutputDto;
import gravit.qa.checklist.dto.internal.ChecklistDraftOutputDto;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.dto.internal.SpecRowDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChecklistDraftValidator {

    private static final String ITEM_PATH = "items[%d]";
    private static final String DELIMITER = ", ";

    public static List<String> validate(
            ChecklistDraftOutputDto output,
            List<Part> featureParts,
            List<SpecRowDto> specRows
    ) {
        List<String> errors = new ArrayList<>();
        if (output.items() == null || output.items().isEmpty()) {
            errors.add("items에 체크 항목이 1개 이상 있어야 합니다.");
            return errors;
        }

        Map<String, SpecRowDto> rowIdToRow = new LinkedHashMap<>();
        specRows.forEach(row -> rowIdToRow.put(row.rowId(), row));
        Set<String> coveredRowIds = new HashSet<>();
        Set<String> titles = new HashSet<>();

        for (int index = 0; index < output.items().size(); index++) {
            ChecklistDraftItemOutputDto item = output.items().get(index);
            String path = ITEM_PATH.formatted(index);
            requireText(item.evidence(), path + ".evidence", errors);
            requireText(item.title(), path + ".title", errors);
            requireText(item.expected(), path + ".expected", errors);
            requireText(item.classificationReason(), path + ".classificationReason", errors);
            if (item.title() != null && !item.title().isBlank() && !titles.add(item.title().trim())) {
                errors.add(path + ".title '" + item.title().trim() + "'과 행위가 같은 항목이 이미 있습니다. 하나로 묶으세요.");
            }
            validateParts(item.parts(), featureParts, path, errors);
            List<SpecRowDto> rows = validateRowIds(item.specRowIds(), rowIdToRow, path, errors);
            coveredRowIds.addAll(rows.stream().map(SpecRowDto::rowId).toList());
            validatePlatformTags(item.parts(), rows, path, errors);
        }

        Set<String> missing = new LinkedHashSet<>(rowIdToRow.keySet());
        missing.removeAll(coveredRowIds);
        if (!missing.isEmpty()) {
            errors.add("어느 항목도 다루지 않은 명세 행이 있습니다: " + String.join(DELIMITER, missing));
        }
        return errors;
    }

    private static void requireText(
            String value,
            String field,
            List<String> errors
    ) {
        if (value == null || value.isBlank()) {
            errors.add(field + "가 비어 있습니다.");
        }
    }

    private static void validateParts(
            List<Part> parts,
            List<Part> featureParts,
            String path,
            List<String> errors
    ) {
        if (parts == null || parts.isEmpty()) {
            errors.add(path + ".parts에 파트가 1개 이상 있어야 합니다.");
            return;
        }
        if (new HashSet<>(parts).size() != parts.size()) {
            errors.add(path + ".parts에 같은 파트가 중복됩니다.");
        }
        parts.stream()
                .filter(part -> !featureParts.contains(part))
                .distinct()
                .forEach(part -> errors.add(path + ".parts의 " + part + "는 기능 파트 " + featureParts + "에 없습니다."));
    }

    private static List<SpecRowDto> validateRowIds(
            List<String> rowIds,
            Map<String, SpecRowDto> rowIdToRow,
            String path,
            List<String> errors
    ) {
        if (rowIdToRow.isEmpty()) {
            return List.of();
        }
        if (rowIds == null || rowIds.isEmpty()) {
            errors.add(path + ".specRowIds에 다루는 명세 행 번호가 1개 이상 있어야 합니다.");
            return List.of();
        }
        List<SpecRowDto> rows = new ArrayList<>();
        for (String rowId : rowIds) {
            SpecRowDto row = rowIdToRow.get(rowId == null ? null : rowId.trim());
            if (row == null) {
                errors.add(path + ".specRowIds의 '" + rowId + "'는 명세에 없는 행 번호입니다.");
            } else {
                rows.add(row);
            }
        }
        return rows;
    }

    private static void validatePlatformTags(
            List<Part> parts,
            List<SpecRowDto> rows,
            String path,
            List<String> errors
    ) {
        if (parts == null || rows.isEmpty()) {
            return;
        }
        boolean anyWeb = rows.stream().anyMatch(SpecRowDto::appliesToWeb);
        boolean anyApp = rows.stream().anyMatch(SpecRowDto::appliesToApp);
        if (parts.contains(Part.APP) && !anyApp) {
            errors.add(path + ": 다루는 명세 행이 모두 [웹] 행인데 parts에 APP이 있습니다.");
        }
        if (parts.contains(Part.WEB) && !anyWeb) {
            errors.add(path + ": 다루는 명세 행이 모두 [앱] 행인데 parts에 WEB이 있습니다.");
        }
    }
}
