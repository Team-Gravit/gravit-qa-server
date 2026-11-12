package gravit.qa.feature.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum Part {

    SERVER("서버"),
    WEB("웹"),
    APP("앱");

    private final String notionLabel;

    public static Optional<Part> fromNotionLabel(String label) {
        String trimmed = label == null ? "" : label.trim();
        return Arrays.stream(values())
                .filter(part -> part.notionLabel.equals(trimmed))
                .findFirst();
    }

    public static List<String> toNames(Collection<Part> parts) {
        return parts.stream().map(Part::name).toList();
    }

    public static List<Part> fromNames(Collection<String> names) {
        return names.stream().map(Part::valueOf).toList();
    }
}
