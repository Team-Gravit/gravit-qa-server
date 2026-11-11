package gravit.qa.sprint.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SprintStepId implements Serializable {

    private Long sprintId;
    private SetupStepKey stepKey;

    public static SprintStepId of(
            long sprintId,
            SetupStepKey stepKey
    ) {
        return new SprintStepId(sprintId, stepKey);
    }
}
