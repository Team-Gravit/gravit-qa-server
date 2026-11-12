package gravit.qa.sprint.dto.internal;

import gravit.qa.sprint.domain.SetupStepKey;
import gravit.qa.sprint.domain.SetupStepStatus;
import gravit.qa.sprint.domain.SprintStep;

public record SetupStepDto(
        SetupStepKey key,
        SetupStepStatus status,
        int attempt,
        String message
) {

    public static SetupStepDto from(SprintStep step) {
        return new SetupStepDto(step.getStepKey(), step.getStatus(), step.getAttempt(), step.getMessage());
    }

    public static SetupStepDto pending(SetupStepKey key) {
        return new SetupStepDto(key, SetupStepStatus.PENDING, 0, null);
    }
}
