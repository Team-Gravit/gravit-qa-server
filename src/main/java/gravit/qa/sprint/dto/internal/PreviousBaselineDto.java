package gravit.qa.sprint.dto.internal;

import java.util.Map;

public record PreviousBaselineDto(
        long baselineId,
        Map<String, String> repoToCommitSha,
        Integer aosVersionCode
) {

    public static PreviousBaselineDto of(
            long baselineId,
            Map<String, String> repoToCommitSha,
            Integer aosVersionCode
    ) {
        return new PreviousBaselineDto(baselineId, Map.copyOf(repoToCommitSha), aosVersionCode);
    }
}
