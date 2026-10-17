package gravit.qa.sprint.dto.internal;

import gravit.qa.adapter.github.dto.internal.GithubChangedFileDto;

import java.util.List;
import java.util.Map;

public record SetupDiffDto(
        Map<String, List<GithubChangedFileDto>> repoToFiles,
        List<SetupSnapshotDto> snapshots
) {

    public static SetupDiffDto of(
            Map<String, List<GithubChangedFileDto>> repoToFiles,
            List<SetupSnapshotDto> snapshots
    ) {
        return new SetupDiffDto(repoToFiles, List.copyOf(snapshots));
    }

    public static SetupDiffDto empty() {
        return new SetupDiffDto(Map.of(), List.of());
    }

    public boolean isEmpty() {
        return repoToFiles.isEmpty();
    }
}
