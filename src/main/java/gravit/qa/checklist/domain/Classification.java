package gravit.qa.checklist.domain;

import java.util.Optional;

public enum Classification {
    AUTO,
    MANUAL;

    private static final int FIRST_ROUND = 1;
    private static final int SECOND_ROUND = 2;

    public static Optional<Classification> ofRound(Integer round) {
        if (round == null) {
            return Optional.empty();
        }
        if (round == FIRST_ROUND) {
            return Optional.of(AUTO);
        }
        if (round == SECOND_ROUND) {
            return Optional.of(MANUAL);
        }
        return Optional.empty();
    }
}
