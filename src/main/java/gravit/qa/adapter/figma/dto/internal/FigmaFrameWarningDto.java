package gravit.qa.adapter.figma.dto.internal;

import gravit.qa.adapter.figma.domain.FigmaFrameWarningType;
import gravit.qa.adapter.figma.domain.FigmaPlatform;

public record FigmaFrameWarningDto(
        FigmaFrameWarningType type,

        FigmaPlatform platform,

        String nodeId,

        String name
) {
}
