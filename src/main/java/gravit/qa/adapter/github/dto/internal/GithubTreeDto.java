package gravit.qa.adapter.github.dto.internal;

import java.util.List;

public record GithubTreeDto(
        String commitSha,
        boolean truncated,
        List<GithubTreeEntryDto> entries
) {
}
