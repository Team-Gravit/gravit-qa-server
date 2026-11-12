package gravit.qa.feature.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum SpecStatus {

    DRAFT("역문서화 초안"),
    NEEDS_CHECK("확인 필요"),
    CONFIRMED("확정");

    private final String notionLabel;

    public static Optional<SpecStatus> fromNotionLabel(String label) {
        String trimmed = label == null ? "" : label.trim();
        return Arrays.stream(values())
                .filter(status -> status.notionLabel.equals(trimmed))
                .findFirst();
    }
}
