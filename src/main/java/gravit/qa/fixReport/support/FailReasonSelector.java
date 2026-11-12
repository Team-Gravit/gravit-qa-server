package gravit.qa.fixReport.support;

import gravit.qa.checklist.dto.internal.CodeEvidenceDto;
import gravit.qa.checklist.dto.internal.FailRecordDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FailReasonSelector {

    public static String reason(
            String current,
            List<FailRecordDto> newestFirst
    ) {
        if (StringUtils.hasText(current)) {
            return current;
        }
        return newestFirst.stream()
                .map(FailRecordDto::reason)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    public static List<CodeEvidenceDto> evidence(
            List<CodeEvidenceDto> current,
            List<FailRecordDto> newestFirst
    ) {
        if (!current.isEmpty()) {
            return current;
        }
        return newestFirst.stream()
                .map(FailRecordDto::evidence)
                .filter(evidence -> !evidence.isEmpty())
                .findFirst()
                .orElse(List.of());
    }
}
