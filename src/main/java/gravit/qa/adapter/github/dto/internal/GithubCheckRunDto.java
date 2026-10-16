package gravit.qa.adapter.github.dto.internal;

import java.time.OffsetDateTime;

public record GithubCheckRunDto(
        String name,
        String status,
        String conclusion,
        String detailsUrl,
        OffsetDateTime completedAt
) {
}
