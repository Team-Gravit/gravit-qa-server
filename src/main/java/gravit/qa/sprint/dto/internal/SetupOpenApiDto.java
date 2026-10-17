package gravit.qa.sprint.dto.internal;

import gravit.qa.adapter.openapi.dto.internal.OpenApiExcerptDto;

import java.util.List;
import java.util.Map;

public record SetupOpenApiDto(
        Map<String, OpenApiExcerptDto> screenIdToExcerpt,
        Map<String, List<WarningDto>> screenIdToWarnings,
        SetupSnapshotDto snapshot
) {

    public static SetupOpenApiDto of(
            Map<String, OpenApiExcerptDto> screenIdToExcerpt,
            Map<String, List<WarningDto>> screenIdToWarnings,
            SetupSnapshotDto snapshot
    ) {
        return new SetupOpenApiDto(Map.copyOf(screenIdToExcerpt), Map.copyOf(screenIdToWarnings), snapshot);
    }

    public OpenApiExcerptDto excerptOf(String screenId) {
        return screenIdToExcerpt.get(screenId);
    }

    public List<WarningDto> warningsOf(String screenId) {
        return screenIdToWarnings.getOrDefault(screenId, List.of());
    }
}
