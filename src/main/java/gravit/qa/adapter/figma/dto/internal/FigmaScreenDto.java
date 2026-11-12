package gravit.qa.adapter.figma.dto.internal;

import gravit.qa.adapter.figma.domain.FigmaPlatform;

import java.util.List;
import java.util.Objects;

public record FigmaScreenDto(
        String screenId,
        FigmaPlatform platform,
        List<FigmaFrameDto> frames
) {

    public List<String> states() {
        return frames.stream()
                .map(FigmaFrameDto::state)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
