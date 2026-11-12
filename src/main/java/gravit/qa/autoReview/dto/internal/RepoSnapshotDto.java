package gravit.qa.autoReview.dto.internal;

import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.adapter.github.dto.internal.GithubTreeEntryDto;

import java.util.Map;
import java.util.Optional;

public record RepoSnapshotDto(
        GithubRepo repo,
        String commitSha,
        Map<String, GithubTreeEntryDto> pathToEntry
) {

    public Optional<GithubTreeEntryDto> find(String path) {
        return Optional.ofNullable(pathToEntry.get(path));
    }
}
