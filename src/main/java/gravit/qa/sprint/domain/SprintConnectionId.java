package gravit.qa.sprint.domain;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SprintConnectionId implements Serializable {

    private Long sprintId;
    private ConnectionTarget target;
}
