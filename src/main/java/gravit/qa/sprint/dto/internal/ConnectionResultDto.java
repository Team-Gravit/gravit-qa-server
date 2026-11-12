package gravit.qa.sprint.dto.internal;

import gravit.qa.sprint.domain.ConnectionTarget;
import gravit.qa.sprint.domain.SprintConnection;

public record ConnectionResultDto(
        ConnectionTarget target,
        boolean connected,
        String branch,
        String commitSha,
        String message
) {

    public static ConnectionResultDto connected(ConnectionTarget target) {
        return new ConnectionResultDto(target, true, null, null, null);
    }

    public static ConnectionResultDto connected(
            ConnectionTarget target,
            String branch,
            String commitSha
    ) {
        return new ConnectionResultDto(target, true, branch, commitSha, null);
    }

    public static ConnectionResultDto failed(
            ConnectionTarget target,
            String message
    ) {
        return new ConnectionResultDto(target, false, null, null, message);
    }

    public static ConnectionResultDto from(SprintConnection connection) {
        return new ConnectionResultDto(
                connection.getTarget(),
                connection.isConnected(),
                connection.getBranch(),
                connection.getCommitSha(),
                connection.getMessage()
        );
    }
}
