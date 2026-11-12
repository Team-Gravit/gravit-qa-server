package gravit.qa.sprint.domain;

import java.util.EnumSet;
import java.util.Set;

public enum SprintStatus {
    DRAFT,
    SPEC_LOAD,
    CHECKLIST,
    DESIGN_REVIEW,
    AUTO_REVIEW,
    MANUAL_REVIEW,
    CLOSED;

    private static final Set<SprintStatus> QA_PREPARE_STATUSES = EnumSet.of(CHECKLIST, DESIGN_REVIEW, AUTO_REVIEW);
    private static final Set<SprintStatus> CHECKLIST_EDITABLE_STATUSES = EnumSet.of(DESIGN_REVIEW, AUTO_REVIEW, MANUAL_REVIEW);

    public boolean isQaPreparing() {
        return QA_PREPARE_STATUSES.contains(this);
    }

    public boolean allowsChecklistEdit() {
        return CHECKLIST_EDITABLE_STATUSES.contains(this);
    }
}
