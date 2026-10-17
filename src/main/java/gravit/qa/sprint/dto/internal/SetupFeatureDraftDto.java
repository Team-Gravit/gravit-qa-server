package gravit.qa.sprint.dto.internal;

import gravit.qa.checklist.dto.internal.ChecklistItemDraftDto;
import gravit.qa.feature.dto.internal.FeatureUpsertDto;

import java.util.List;

public record SetupFeatureDraftDto(
        SetupFeatureSpecDto spec,
        List<String> figmaNodeIds,
        List<WarningDto> warnings,
        List<ChecklistItemDraftDto> items
) {

    public static SetupFeatureDraftDto of(
            SetupFeatureSpecDto spec,
            List<String> figmaNodeIds,
            List<WarningDto> warnings,
            List<ChecklistItemDraftDto> items
    ) {
        return new SetupFeatureDraftDto(spec, List.copyOf(figmaNodeIds), List.copyOf(warnings), List.copyOf(items));
    }

    public String screenId() {
        return spec.screenId();
    }

    public FeatureUpsertDto toUpsertDto() {
        String name = spec.spec().name();
        return FeatureUpsertDto.of(
                spec.screenId(),
                name == null || name.isBlank() ? spec.screenId() : name.trim(),
                spec.spec().route(),
                spec.parts(),
                spec.spec().pageId(),
                spec.specStatus(),
                spec.spec().relatedOperationIds()
        );
    }
}
