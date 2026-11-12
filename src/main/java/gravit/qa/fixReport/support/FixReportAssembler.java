package gravit.qa.fixReport.support;

import gravit.qa.checklist.dto.internal.FailedCellDto;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.designReview.dto.internal.FailedDesignItemDto;
import gravit.qa.feature.dto.internal.FeatureDto;
import gravit.qa.fixReport.dto.internal.FixReportDto;
import gravit.qa.fixReport.dto.internal.FixReportEntryDto;
import gravit.qa.fixReport.dto.internal.FixReportScreenDto;
import gravit.qa.sprint.domain.ProgressPart;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FixReportAssembler {

    private static final Comparator<FailedCellDto> CELL_ORDER = Comparator.comparing(FailedCellDto::classification)
            .thenComparingInt(FailedCellDto::sortOrder)
            .thenComparingLong(FailedCellDto::itemId);
    private static final Comparator<FailedDesignItemDto> DESIGN_ITEM_ORDER = Comparator.comparingInt(FailedDesignItemDto::sortOrder)
            .thenComparingLong(FailedDesignItemDto::itemId);
    private static final Comparator<FixReportScreenDto> SCREEN_ORDER = Comparator.comparing(FixReportScreenDto::screenId)
            .thenComparingLong(FixReportScreenDto::featureId)
            .thenComparing(FixReportScreenDto::platform, Comparator.nullsFirst(Comparator.naturalOrder()));

    public static FixReportDto assembleCode(
            String sprintKey,
            ProgressPart part,
            String repoName,
            String ref,
            List<FailedCellDto> cells,
            Map<Long, FeatureDto> featureIdToFeature
    ) {
        Map<Long, List<FailedCellDto>> featureIdToCells = cells.stream()
                .collect(Collectors.groupingBy(FailedCellDto::featureId, LinkedHashMap::new, Collectors.toList()));

        List<FixReportScreenDto> screens = featureIdToCells.entrySet().stream()
                .map(entry -> toScreen(
                        featureIdToFeature.get(entry.getKey()),
                        null,
                        entry.getValue().stream().sorted(CELL_ORDER).map(FixReportAssembler::toEntry).toList()
                ))
                .sorted(SCREEN_ORDER)
                .toList();

        return FixReportDto.code(sprintKey, part, repoName, ref, screens);
    }

    public static FixReportDto assembleDesign(
            String sprintKey,
            String figmaFileKey,
            List<FailedDesignItemDto> items,
            Map<Long, FeatureDto> featureIdToFeature
    ) {
        Map<Long, Map<DesignPlatform, List<FailedDesignItemDto>>> featureIdToPlatformItems = items.stream()
                .collect(Collectors.groupingBy(
                        FailedDesignItemDto::featureId,
                        LinkedHashMap::new,
                        Collectors.groupingBy(FailedDesignItemDto::platform, () -> new EnumMap<>(DesignPlatform.class), Collectors.toList())
                ));

        List<FixReportScreenDto> screens = new ArrayList<>();
        featureIdToPlatformItems.forEach((featureId, platformToItems) -> platformToItems.forEach((platform, platformItems) -> screens.add(toScreen(
                featureIdToFeature.get(featureId),
                platform,
                platformItems.stream().sorted(DESIGN_ITEM_ORDER).map(FixReportAssembler::toEntry).toList()
        ))));
        screens.sort(SCREEN_ORDER);

        return FixReportDto.design(sprintKey, figmaFileKey, screens);
    }

    private static FixReportScreenDto toScreen(
            FeatureDto feature,
            DesignPlatform platform,
            List<FixReportEntryDto> entries
    ) {
        return FixReportScreenDto.of(feature.id(), feature.screenId(), feature.name(), feature.notionPageId(), platform, entries);
    }

    private static FixReportEntryDto toEntry(FailedCellDto cell) {
        return FixReportEntryDto.of(
                cell.title(),
                cell.expected(),
                FailReasonSelector.reason(cell.reason(), cell.failRecords()),
                cell.specRowIds(),
                FailReasonSelector.evidence(cell.evidence(), cell.failRecords()),
                List.of()
        );
    }

    private static FixReportEntryDto toEntry(FailedDesignItemDto item) {
        return FixReportEntryDto.of(
                item.title(),
                item.expected(),
                FailReasonSelector.reason(item.reason(), item.failRecords()),
                item.specRowIds(),
                List.of(),
                item.figmaNodeIds()
        );
    }
}
