package gravit.qa.reflection.dto.internal;

import gravit.qa.reflection.domain.ReflectionState;
import gravit.qa.reflection.domain.SpecReflection;

import java.util.List;
import java.util.Map;

public record SpecReflectionDto(
        long id,
        long sprintId,
        long featureId,
        List<ReflectionHistoryRowDto> historyRows,
        List<ReflectionOperationDto> operations,
        Map<String, String> baseBlocks,
        String beforeMarkdown,
        String afterMarkdown,
        boolean appliable,
        ReflectionState state,
        String message,
        int version
) {

    public static SpecReflectionDto from(SpecReflection reflection) {
        return new SpecReflectionDto(
                reflection.getId(),
                reflection.getSprintId(),
                reflection.getFeatureId(),
                List.copyOf(reflection.getHistoryRows()),
                List.copyOf(reflection.getOperations()),
                Map.copyOf(reflection.getBaseBlocks()),
                reflection.getBeforeMarkdown(),
                reflection.getAfterMarkdown(),
                reflection.isAppliable(),
                reflection.getState(),
                reflection.getMessage(),
                reflection.getVersion()
        );
    }

    public boolean hasAppliedOperation() {
        return operations.stream().anyMatch(ReflectionOperationDto::applied);
    }
}
