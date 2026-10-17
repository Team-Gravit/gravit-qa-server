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
public class BaselineRepoId implements Serializable {

    private Long baselineId;
    private String repo;

    public static BaselineRepoId of(
            long baselineId,
            String repo
    ) {
        return new BaselineRepoId(baselineId, repo);
    }
}
