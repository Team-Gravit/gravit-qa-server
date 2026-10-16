package gravit.qa.adapter.github.dto.internal;

import java.util.List;

public record GithubCompareDto(
        String baseSha,

        String headSha,

        String status,

        int aheadBy,

        int behindBy,

        int totalCommits,

        List<GithubChangedFileDto> files,

        String rawJson
) {
}
