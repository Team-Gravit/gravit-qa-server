package gravit.qa.reflection.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ReflectionState {
    PENDING,
    APPROVED,
    REJECTED,
    APPLIED,
    FAILED;

    public static final Set<ReflectionState> UNRESOLVED = EnumSet.of(PENDING, APPROVED, FAILED);
}
