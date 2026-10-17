package gravit.qa.sprint.dto.internal;

import gravit.qa.sprint.domain.WarningCode;

public record WarningDto(
        WarningCode code,
        String message
) {

    public static WarningDto of(
            WarningCode code,
            String message
    ) {
        return new WarningDto(code, message);
    }
}
