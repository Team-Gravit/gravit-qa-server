package gravit.qa.sprint.dto.internal;

import java.util.List;

public record SetupBaselineDto(
        Long previousBaselineId,
        List<SetupBaselineRepoDto> repos
) {

    public static SetupBaselineDto of(
            Long previousBaselineId,
            List<SetupBaselineRepoDto> repos
    ) {
        return new SetupBaselineDto(previousBaselineId, List.copyOf(repos));
    }
}
