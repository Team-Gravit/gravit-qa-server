package gravit.qa.adapter.figma.dto.internal;

import java.util.List;
import java.util.Map;

public record FigmaFrameImagesDto(
        Map<String, byte[]> nodeIdToPng,
        List<String> failedNodeIds
) {
}
