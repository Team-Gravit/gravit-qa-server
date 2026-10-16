package gravit.qa.adapter.figma.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.adapter.figma.domain.FigmaFrameWarningType;
import gravit.qa.adapter.figma.domain.FigmaPlatform;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameDto;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameWarningDto;
import gravit.qa.adapter.figma.dto.internal.FigmaQaFramesDto;
import gravit.qa.adapter.figma.dto.internal.FigmaScreenDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.FRAME_OUTSIDE_PLATFORM;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.INVALID_FRAME_NAME;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.QA_PAGE_NOT_FOUND;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.SPRINT_SECTION_NOT_FOUND;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.UNCONFIRMED_FRAME;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.UNKNOWN_SECTION;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FigmaFrameParser {

    public static final Pattern SCREEN_ID = Pattern.compile("^(O|M|L|LG|MY|A|E|W|R)\\.\\d+([.-]\\d+)*$");
    public static final String QA_PAGE_NAME = "QA";

    private static final String UNCONFIRMED_PREFIX = "미확인/";
    private static final String SECTION = "SECTION";
    private static final String FRAME = "FRAME";
    private static final String CHILDREN = "children";

    public static FigmaQaFramesDto qaPageNotFound(String fileKey) {
        return new FigmaQaFramesDto(
                fileKey,
                null,
                null,
                List.of(),
                List.of(new FigmaFrameWarningDto(QA_PAGE_NOT_FOUND, null, null, QA_PAGE_NAME))
        );
    }

    public static FigmaQaFramesDto parse(
            String fileKey,
            JsonNode qaPage,
            String sprintKey
    ) {
        String qaPageId = qaPage.path("id").asText();
        Optional<JsonNode> sprintSection = findSprintSection(qaPage, sprintKey);
        if (sprintSection.isEmpty()) {
            return new FigmaQaFramesDto(
                    fileKey,
                    qaPageId,
                    null,
                    List.of(),
                    List.of(new FigmaFrameWarningDto(SPRINT_SECTION_NOT_FOUND, null, null, sprintKey))
            );
        }

        Map<FigmaPlatform, Map<String, List<FigmaFrameDto>>> platformToScreens = new EnumMap<>(FigmaPlatform.class);
        List<FigmaFrameWarningDto> warnings = new ArrayList<>();
        for (JsonNode child : sprintSection.get().path(CHILDREN)) {
            String type = child.path("type").asText();
            if (SECTION.equals(type)) {
                Optional<FigmaPlatform> platform = FigmaPlatform.fromSectionName(nameOf(child));
                if (platform.isPresent()) {
                    Map<String, List<FigmaFrameDto>> screens = platformToScreens.computeIfAbsent(platform.get(), key -> new LinkedHashMap<>());
                    collectFrames(child, platform.get(), screens, warnings);
                } else {
                    warnings.add(warning(UNKNOWN_SECTION, null, child));
                }
            } else if (FRAME.equals(type)) {
                warnings.add(warning(FRAME_OUTSIDE_PLATFORM, null, child));
            }
        }

        return new FigmaQaFramesDto(
                fileKey,
                qaPageId,
                sprintSection.get().path("id").asText(),
                toScreens(platformToScreens),
                warnings
        );
    }

    public static List<ObjectNode> findTruncatedSections(
            JsonNode qaPage,
            String sprintKey
    ) {
        List<ObjectNode> truncated = new ArrayList<>();
        findSprintSection(qaPage, sprintKey).ifPresent(section -> collectTruncated(section, truncated));
        return truncated;
    }

    public static boolean isScreenId(String name) {
        return name != null && SCREEN_ID.matcher(name.trim()).matches();
    }

    private static Optional<JsonNode> findSprintSection(
            JsonNode qaPage,
            String sprintKey
    ) {
        String target = sprintKey == null ? "" : sprintKey.trim();
        for (JsonNode child : qaPage.path(CHILDREN)) {
            if (SECTION.equals(child.path("type").asText()) && target.equals(nameOf(child))) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    private static void collectFrames(
            JsonNode section,
            FigmaPlatform platform,
            Map<String, List<FigmaFrameDto>> screens,
            List<FigmaFrameWarningDto> warnings
    ) {
        for (JsonNode child : section.path(CHILDREN)) {
            String type = child.path("type").asText();
            if (SECTION.equals(type)) {
                collectFrames(child, platform, screens, warnings);
            } else if (FRAME.equals(type)) {
                classifyFrame(child, nameOf(section), platform, screens, warnings);
            }
        }
    }

    private static void classifyFrame(
            JsonNode frame,
            String sectionName,
            FigmaPlatform platform,
            Map<String, List<FigmaFrameDto>> screens,
            List<FigmaFrameWarningDto> warnings
    ) {
        String name = nameOf(frame);
        if (isScreenId(name)) {
            screens.computeIfAbsent(name, key -> new ArrayList<>())
                    .add(new FigmaFrameDto(frame.path("id").asText(), name, sectionName));
            return;
        }

        FigmaFrameWarningType type = name.startsWith(UNCONFIRMED_PREFIX) ? UNCONFIRMED_FRAME : INVALID_FRAME_NAME;
        warnings.add(warning(type, platform, frame));
    }

    private static void collectTruncated(
            JsonNode section,
            List<ObjectNode> truncated
    ) {
        for (JsonNode child : section.path(CHILDREN)) {
            if (!SECTION.equals(child.path("type").asText())) {
                continue;
            }
            if (child.has(CHILDREN)) {
                collectTruncated(child, truncated);
            } else if (child instanceof ObjectNode objectNode) {
                truncated.add(objectNode);
            }
        }
    }

    private static List<FigmaScreenDto> toScreens(Map<FigmaPlatform, Map<String, List<FigmaFrameDto>>> platformToScreens) {
        List<FigmaScreenDto> screens = new ArrayList<>();
        platformToScreens.forEach((platform, screenIdToFrames) -> screenIdToFrames.forEach(
                (screenId, frames) -> screens.add(new FigmaScreenDto(screenId, platform, List.copyOf(frames)))
        ));
        return screens;
    }

    private static FigmaFrameWarningDto warning(
            FigmaFrameWarningType type,
            FigmaPlatform platform,
            JsonNode node
    ) {
        return new FigmaFrameWarningDto(type, platform, node.path("id").asText(), nameOf(node));
    }

    private static String nameOf(JsonNode node) {
        return node.path("name").asText().trim();
    }
}
