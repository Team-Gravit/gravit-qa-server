package gravit.qa.adapter.figma.dto.internal;

import gravit.qa.adapter.figma.domain.FigmaPlatform;

import java.util.List;

public record FigmaScreenDto(
        String screenId,

        FigmaPlatform platform,

        List<FigmaFrameDto> frames
) {

    public boolean hasStateVariants() {
        return frames.size() > 1;
    }
}
