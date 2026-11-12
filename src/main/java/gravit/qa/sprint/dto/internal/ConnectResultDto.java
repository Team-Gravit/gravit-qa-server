package gravit.qa.sprint.dto.internal;

import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;

import java.util.List;
import java.util.stream.Collectors;

public record ConnectResultDto(
        NotionSprintDto notionSprint,
        String figmaFileKey,
        List<ConnectionResultDto> connections,
        SetupSnapshotDto snapshot,
        boolean retryable
) {

    private static final String FAILURE_DELIMITER = "; ";

    public boolean isSuccess() {
        return connections.stream().allMatch(ConnectionResultDto::connected);
    }

    public String failureMessage() {
        return "연결 실패: " + connections.stream()
                .filter(connection -> !connection.connected())
                .map(connection -> connection.target() + "(" + connection.message() + ")")
                .collect(Collectors.joining(FAILURE_DELIMITER));
    }
}
