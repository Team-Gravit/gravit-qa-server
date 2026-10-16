package gravit.qa.adapter.figma.domain;

import java.util.Arrays;
import java.util.Optional;

public enum FigmaPlatform {

    WEB,
    AOS;

    public static Optional<FigmaPlatform> fromSectionName(String sectionName) {
        String trimmed = sectionName == null ? "" : sectionName.trim();
        return Arrays.stream(values())
                .filter(platform -> platform.name().equals(trimmed))
                .findFirst();
    }
}
