package gravit.qa.feature.dto.internal;

import gravit.qa.feature.domain.SpecSection;

import java.util.List;

public record SpecRowDto(
        String rowId,
        SpecSection section,
        List<String> columns,
        List<String> cells
) {

    public static final String WEB_TAG = "[웹]";
    public static final String APP_TAG = "[앱]";

    private static final String CELL_DELIMITER = " | ";

    public static SpecRowDto of(
            String rowId,
            SpecSection section,
            List<String> columns,
            List<String> cells
    ) {
        return new SpecRowDto(rowId, section, List.copyOf(columns), List.copyOf(cells));
    }

    public String text() {
        return String.join(CELL_DELIMITER, cells);
    }

    public boolean appliesToWeb() {
        return mentions(WEB_TAG) || !mentions(APP_TAG);
    }

    public boolean appliesToApp() {
        return mentions(APP_TAG) || !mentions(WEB_TAG);
    }

    private boolean mentions(String tag) {
        return cells.stream().skip(1).anyMatch(cell -> cell.contains(tag));
    }
}
