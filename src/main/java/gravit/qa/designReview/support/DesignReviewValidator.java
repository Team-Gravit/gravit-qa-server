package gravit.qa.designReview.support;

import gravit.qa.checklist.domain.Verdict;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.designReview.dto.internal.DesignReviewItemOutputDto;
import gravit.qa.designReview.dto.internal.DesignReviewOutputDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DesignReviewValidator {

    private static final String ITEM_PATH = "items[%d]";
    private static final String DELIMITER = ", ";

    public static List<String> validate(
            DesignReviewOutputDto output,
            Map<DesignPlatform, List<String>> platformToRowIds,
            Map<DesignPlatform, Set<String>> platformToNodeIds
    ) {
        List<String> errors = new ArrayList<>();
        if (output.items() == null || output.items().isEmpty()) {
            errors.add("items에 디자인 검수 항목이 1개 이상 있어야 합니다.");
            return errors;
        }

        Map<DesignPlatform, Set<String>> platformToCovered = new EnumMap<>(DesignPlatform.class);
        for (int index = 0; index < output.items().size(); index++) {
            DesignReviewItemOutputDto item = output.items().get(index);
            String path = ITEM_PATH.formatted(index);
            if (item.platform() == null || !platformToRowIds.containsKey(item.platform())) {
                errors.add(path + ".platform " + item.platform() + "는 이번 입력의 플랫폼 " + platformToRowIds.keySet() + "에 없습니다.");
                continue;
            }
            requireText(item.evidence(), path + ".evidence", errors);
            requireText(item.title(), path + ".title", errors);
            requireText(item.expected(), path + ".expected", errors);
            requireText(item.reason(), path + ".reason", errors);

            Set<String> covered = platformToCovered.computeIfAbsent(item.platform(), platform -> new HashSet<>());
            List<String> rowIds = platformToRowIds.get(item.platform());
            if (item.specRowIds() == null || item.specRowIds().isEmpty()) {
                errors.add(path + ".specRowIds에 C행 번호가 1개 이상 있어야 합니다.");
            } else {
                for (String rowId : item.specRowIds()) {
                    String trimmed = rowId == null ? null : rowId.trim();
                    if (!rowIds.contains(trimmed)) {
                        errors.add(path + ".specRowIds의 '" + rowId + "'는 " + item.platform() + " C행이 아닙니다.");
                    } else if (!covered.add(trimmed)) {
                        errors.add(path + ".specRowIds의 '" + rowId + "'가 " + item.platform() + "의 다른 항목에도 있습니다. 행은 한 항목에만 넣습니다.");
                    }
                }
            }

            Set<String> nodeIds = platformToNodeIds.getOrDefault(item.platform(), Set.of());
            List<String> figmaNodeIds = item.figmaNodeIds() == null ? List.of() : item.figmaNodeIds();
            figmaNodeIds.stream()
                    .filter(nodeId -> !nodeIds.contains(nodeId))
                    .forEach(nodeId -> errors.add(path + ".figmaNodeIds의 '" + nodeId + "'는 " + item.platform() + " 레이어 목록에 없습니다."));
            if (item.verdict() == Verdict.PASS && figmaNodeIds.isEmpty()) {
                errors.add(path + ": PASS 판정에는 근거 레이어 ID가 1개 이상 있어야 합니다.");
            }
        }

        platformToRowIds.forEach((platform, rowIds) -> {
            Set<String> missing = new LinkedHashSet<>(rowIds);
            missing.removeAll(platformToCovered.getOrDefault(platform, Set.of()));
            if (!missing.isEmpty()) {
                errors.add(platform + "에서 어느 항목도 다루지 않은 C행이 있습니다: " + String.join(DELIMITER, missing));
            }
        });
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
}
