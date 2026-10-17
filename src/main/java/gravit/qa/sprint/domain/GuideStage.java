package gravit.qa.sprint.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GuideStage {

    PREPARE(1),
    REVIEW(2),
    CHECK(3),
    CLOSE(4),
    DONE(5);

    private final int index;
}
