package gravit.qa.adapter.figma.domain;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@RequiredArgsConstructor
public enum FigmaPlatform {

    WEB(1920),
    AOS(360);

    private static final double FRAME_WIDTH_TOLERANCE = 2;

    private final double frameWidth;

    public static Optional<FigmaPlatform> fromSectionName(String sectionName) {
        String trimmed = sectionName == null ? "" : sectionName.trim();
        return Arrays.stream(values())
                .filter(platform -> platform.name().equals(trimmed))
                .findFirst();
    }

    public boolean fitsFrameWidth(Double width) {
        return width != null && Math.abs(width - frameWidth) <= FRAME_WIDTH_TOLERANCE;
    }
}
