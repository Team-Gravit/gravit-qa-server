package gravit.qa.adapter.notion.dto.internal;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public record NotionSpecDto(
        String pageId,

        String screenId,

        String name,

        List<String> parts,

        String route,

        String relatedApi,

        String figmaUrl,

        String specStatus,

        LocalDate lastVerifiedAt,

        String rawJson
) {

    private static final Pattern OPERATION_ID_DELIMITER = Pattern.compile("[,\\s]+");

    public List<String> relatedOperationIds() {
        if (relatedApi == null || relatedApi.isBlank()) {
            return List.of();
        }
        return Arrays.stream(OPERATION_ID_DELIMITER.split(relatedApi.trim()))
                .filter(operationId -> !operationId.isBlank())
                .distinct()
                .toList();
    }
}
