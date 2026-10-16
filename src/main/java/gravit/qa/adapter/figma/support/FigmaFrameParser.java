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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.FRAME_OUTSIDE_AREA;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.FRAME_OUTSIDE_PLATFORM;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.INVALID_FRAME_NAME;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.NESTED_SECTION;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.PLATFORM_WIDTH_MISMATCH;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.QA_PAGE_NOT_FOUND;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.SPRINT_SECTION_NOT_FOUND;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.UNCONFIRMED_FRAME;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.UNKNOWN_SECTION;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FigmaFrameParser {

    public static final Pattern SCREEN_ID = Pattern.compile("^(?:[A-Z]+(?:-[A-Z]+)?-\\d{2}(?:-\\d{2})?|[A-Z]+-[가-힣]+)$");
    public static final String QA_PAGE_NAME = "QA";

    private static final Pattern FRAME_NAME = Pattern.compile("^(?<id>[^/]+)(?:/(?<state>.+))?$");
    private static final String ID_GROUP = "id";
    private static final String STATE_GROUP = "state";
    private static final String UNCONFIRMED_ID = "미확인";
    private static final Set<String> AREA_SECTION_NAMES = Set.of(
            "MAIN & ONBOARDING",
            "LEARNING",
            "LEAGUE",
            "MY",
            "OTHER",
            "INTERVIEW SIMULATOR"
    );
    private static final String SECTION = "SECTION";
    private static final String FRAME = "FRAME";
    private static final String CHILDREN = "children";
    private static final String FRAME_WIDTH_POINTER = "/absoluteBoundingBox/width";

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
            if (isSection(child)) {
                Optional<FigmaPlatform> platform = FigmaPlatform.fromSectionName(nameOf(child));
                if (platform.isPresent()) {
                    Map<String, List<FigmaFrameDto>> screenIdToFrames = platformToScreens.computeIfAbsent(platform.get(), key -> new LinkedHashMap<>());
                    collectAreas(child, platform.get(), screenIdToFrames, warnings);
                } else {
                    warnings.add(warning(UNKNOWN_SECTION, null, child));
                }
            } else if (isFrame(child)) {
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
            if (isSection(child) && target.equals(nameOf(child))) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    private static void collectAreas(
            JsonNode platformSection,
            FigmaPlatform platform,
            Map<String, List<FigmaFrameDto>> screenIdToFrames,
            List<FigmaFrameWarningDto> warnings
    ) {
        for (JsonNode child : platformSection.path(CHILDREN)) {
            if (isAreaSection(child)) {
                collectFrames(child, platform, screenIdToFrames, warnings);
            } else if (isSection(child)) {
                warnings.add(warning(UNKNOWN_SECTION, platform, child));
            } else if (isFrame(child)) {
                warnings.add(warning(FRAME_OUTSIDE_AREA, platform, child));
            }
        }
    }

    private static void collectFrames(
            JsonNode areaSection,
            FigmaPlatform platform,
            Map<String, List<FigmaFrameDto>> screenIdToFrames,
            List<FigmaFrameWarningDto> warnings
    ) {
        for (JsonNode child : areaSection.path(CHILDREN)) {
            if (isFrame(child)) {
                classifyFrame(child, nameOf(areaSection), platform, screenIdToFrames, warnings);
            } else if (isSection(child)) {
                warnings.add(warning(NESTED_SECTION, platform, child));
            }
        }
    }

    private static void classifyFrame(
            JsonNode frame,
            String areaName,
            FigmaPlatform platform,
            Map<String, List<FigmaFrameDto>> screenIdToFrames,
            List<FigmaFrameWarningDto> warnings
    ) {
        String name = nameOf(frame);
        Matcher matcher = FRAME_NAME.matcher(name);
        if (!matcher.matches()) {
            warnings.add(warning(INVALID_FRAME_NAME, platform, frame));
            return;
        }

        String screenId = matcher.group(ID_GROUP).trim();
        if (UNCONFIRMED_ID.equals(screenId)) {
            warnings.add(warning(UNCONFIRMED_FRAME, platform, frame));
            return;
        }
        if (!isScreenId(screenId)) {
            warnings.add(warning(INVALID_FRAME_NAME, platform, frame));
            return;
        }

        Double width = widthOf(frame);
        if (!platform.fitsFrameWidth(width)) {
            warnings.add(warning(PLATFORM_WIDTH_MISMATCH, platform, frame));
        }

        String state = matcher.group(STATE_GROUP) == null ? null : matcher.group(STATE_GROUP).trim();
        screenIdToFrames.computeIfAbsent(screenId, key -> new ArrayList<>())
                .add(new FigmaFrameDto(frame.path("id").asText(), name, state, areaName, width));
    }

    private static void collectTruncated(
            JsonNode sprintSection,
            List<ObjectNode> truncated
    ) {
        for (JsonNode platformSection : sprintSection.path(CHILDREN)) {
            if (!isPlatformSection(platformSection)) {
                continue;
            }
            if (!platformSection.has(CHILDREN)) {
                truncated.add((ObjectNode) platformSection);
                continue;
            }
            for (JsonNode areaSection : platformSection.path(CHILDREN)) {
                if (isAreaSection(areaSection) && !areaSection.has(CHILDREN)) {
                    truncated.add((ObjectNode) areaSection);
                }
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

    private static boolean isPlatformSection(JsonNode node) {
        return isSection(node) && FigmaPlatform.fromSectionName(nameOf(node)).isPresent();
    }

    private static boolean isAreaSection(JsonNode node) {
        return isSection(node) && AREA_SECTION_NAMES.contains(nameOf(node));
    }

    private static boolean isSection(JsonNode node) {
        return SECTION.equals(node.path("type").asText());
    }

    private static boolean isFrame(JsonNode node) {
        return FRAME.equals(node.path("type").asText());
    }

    private static Double widthOf(JsonNode frame) {
        JsonNode width = frame.at(FRAME_WIDTH_POINTER);
        return width.isNumber() ? width.asDouble() : null;
    }

    private static String nameOf(JsonNode node) {
        return node.path("name").asText().trim();
    }
}
