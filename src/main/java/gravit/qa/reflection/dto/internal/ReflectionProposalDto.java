package gravit.qa.reflection.dto.internal;

import java.util.List;
import java.util.Map;

public record ReflectionProposalDto(
        List<ReflectionHistoryRowDto> historyRows,
        List<ReflectionOperationDto> operations,
        Map<String, String> baseBlocks,
        String beforeMarkdown,
        String afterMarkdown,
        boolean appliable,
        String message,
        int promptChars
) {

    public static ReflectionProposalDto proposed(
            List<ReflectionHistoryRowDto> historyRows,
            List<ReflectionOperationDto> operations,
            Map<String, String> baseBlocks,
            String beforeMarkdown,
            String afterMarkdown,
            int promptChars
    ) {
        return new ReflectionProposalDto(List.copyOf(historyRows), List.copyOf(operations), Map.copyOf(baseBlocks), beforeMarkdown, afterMarkdown, true, null, promptChars);
    }

    public static ReflectionProposalDto failed(
            List<ReflectionHistoryRowDto> historyRows,
            String beforeMarkdown,
            String message,
            int promptChars
    ) {
        return new ReflectionProposalDto(List.copyOf(historyRows), List.of(), Map.of(), beforeMarkdown, beforeMarkdown, false, message, promptChars);
    }
}
