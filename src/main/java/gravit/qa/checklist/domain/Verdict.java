package gravit.qa.checklist.domain;

public enum Verdict {
    PASS,
    FAIL,
    UNKNOWN;

    public CellState toCellState() {
        return switch (this) {
            case PASS -> CellState.PASS;
            case FAIL -> CellState.FAIL;
            case UNKNOWN -> CellState.UNCHECKED;
        };
    }
}
