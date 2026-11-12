package gravit.qa.adapter.figma.dto.internal;

import gravit.qa.adapter.figma.domain.FigmaPlatform;

import java.util.List;
import java.util.Optional;

public record FigmaQaFramesDto(
        String fileKey,
        String qaPageId,
        String sprintSectionId,
        List<FigmaScreenDto> screens,
        List<FigmaFrameWarningDto> warnings
) {

    public Optional<FigmaScreenDto> findScreen(
            FigmaPlatform platform,
            String screenId
    ) {
        return screens.stream()
                .filter(screen -> screen.platform() == platform && screen.screenId().equals(screenId))
                .findFirst();
    }
}
