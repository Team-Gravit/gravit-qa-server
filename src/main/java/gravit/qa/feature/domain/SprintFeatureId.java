package gravit.qa.feature.domain;

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
public class SprintFeatureId implements Serializable {

    private Long sprintId;
    private Long featureId;

    public static SprintFeatureId of(
            long sprintId,
            long featureId
    ) {
        return new SprintFeatureId(sprintId, featureId);
    }
}
