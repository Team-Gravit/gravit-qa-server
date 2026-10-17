package gravit.qa.sprint.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.adapter.figma.domain.FigmaFrameWarningType;
import gravit.qa.adapter.figma.domain.FigmaPlatform;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameDto;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameImagesDto;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameWarningDto;
import gravit.qa.adapter.figma.dto.internal.FigmaQaFramesDto;
import gravit.qa.adapter.figma.dto.internal.FigmaScreenDto;
import gravit.qa.adapter.figma.infrastructure.FigmaClient;
import gravit.qa.adapter.figma.support.FigmaFileKeys;
import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.adapter.github.dto.internal.AosVersionDto;
import gravit.qa.adapter.github.dto.internal.GithubChangedFileDto;
import gravit.qa.adapter.github.infrastructure.GithubClient;
import gravit.qa.adapter.github.support.AosVersionParser;
import gravit.qa.adapter.notion.dto.internal.NotionPageBodyDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecDto;
import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;
import gravit.qa.adapter.notion.infrastructure.NotionClient;
import gravit.qa.adapter.openapi.dto.internal.OpenApiDocumentDto;
import gravit.qa.adapter.openapi.dto.internal.OpenApiExcerptDto;
import gravit.qa.adapter.openapi.infrastructure.OpenApiClient;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecStatus;
import gravit.qa.global.util.KstTimes;
import gravit.qa.job.support.NonRetryableJobException;
import gravit.qa.snapshot.domain.SnapshotSource;
import gravit.qa.snapshot.infrastructure.SnapshotFileStore;
import gravit.qa.sprint.domain.BaselineRepo;
import gravit.qa.sprint.domain.WarningCode;
import gravit.qa.sprint.dto.internal.PreviousBaselineDto;
import gravit.qa.sprint.dto.internal.SetupBaselineDto;
import gravit.qa.sprint.dto.internal.SetupBaselineRepoDto;
import gravit.qa.sprint.dto.internal.SetupDiffDto;
import gravit.qa.sprint.dto.internal.SetupFeatureSpecDto;
import gravit.qa.sprint.dto.internal.SetupFigmaDto;
import gravit.qa.sprint.dto.internal.SetupNotionDto;
import gravit.qa.sprint.dto.internal.SetupOpenApiDto;
import gravit.qa.sprint.dto.internal.SetupSnapshotDto;
import gravit.qa.sprint.dto.internal.WarningDto;
import gravit.qa.sprint.service.SprintQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SetupCollector {

    public static final Map<Part, FigmaPlatform> PART_TO_PLATFORM = new EnumMap<>(Map.of(
            Part.WEB, FigmaPlatform.WEB,
            Part.APP, FigmaPlatform.AOS
    ));

    private static final String JSON_FILE = "%s.json";
    private static final String FIGMA_FRAME_FILE = "%s_%s.png";
    private static final String NODE_ID_SEPARATOR = ":";
    private static final String FILE_NAME_SEPARATOR = "-";
    private static final String DIFF_SOURCE_REF = "%s@%s..%s";
    private static final String OPENAPI_FILE = "api-docs.json";
    private static final String PAGE_FIELD = "page";
    private static final String MARKDOWN_FIELD = "markdown";
    private static final String BLOCKS_FIELD = "blocks";
    private static final String FILENAME_FIELD = "filename";
    private static final String STATUS_FIELD = "status";
    private static final String ADDITIONS_FIELD = "additions";
    private static final String DELETIONS_FIELD = "deletions";
    private static final String PREVIOUS_FILENAME_FIELD = "previousFilename";
    private static final String DELIMITER = ", ";

    private final SprintQueryService sprintQueryService;

    private final GithubClient githubClient;
    private final NotionClient notionClient;
    private final FigmaClient figmaClient;
    private final OpenApiClient openApiClient;
    private final SnapshotFileStore snapshotFileStore;

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public SetupBaselineDto collectBaseline(long sprintId) {
        Optional<PreviousBaselineDto> previous = sprintQueryService.findPreviousClosedBaseline(sprintId);
        Integer previousVersionCode = previous.map(PreviousBaselineDto::aosVersionCode).orElse(null);

        List<SetupBaselineRepoDto> repos = new ArrayList<>();
        for (GithubRepo repo : GithubRepo.values()) {
            String commitSha = githubClient.getBranchHeadSha(repo, BaselineRepo.RELEASE_BRANCH);
            String previousCommitSha = previous.map(baseline -> baseline.repoToCommitSha().get(repo.getRepoName())).orElse(null);

            if (repo != GithubRepo.GRAVIT_AOS) {
                repos.add(SetupBaselineRepoDto.of(repo.getRepoName(), BaselineRepo.RELEASE_BRANCH, commitSha, previousCommitSha, null, null, false));
                continue;
            }
            String buildGradle = githubClient.getFileContent(repo, AosVersionParser.BUILD_GRADLE_PATH, commitSha);
            AosVersionDto version = AosVersionParser.parse(buildGradle, previousVersionCode);
            if (version.versionCodeWarning()) {
                log.warn("gravit-aos versionCode가 직전 기준점보다 크지 않습니다. sprintId={}, previous={}, current={}", sprintId, previousVersionCode, version.versionCode());
            }
            repos.add(SetupBaselineRepoDto.of(
                    repo.getRepoName(),
                    BaselineRepo.RELEASE_BRANCH,
                    commitSha,
                    previousCommitSha,
                    version.versionCode(),
                    version.versionName(),
                    version.versionCodeWarning()
            ));
        }
        return SetupBaselineDto.of(previous.map(PreviousBaselineDto::baselineId).orElse(null), repos);
    }

    public SetupNotionDto collectNotion(
            long sprintId,
            long jobId,
            String notionPageId
    ) {
        List<SetupSnapshotDto> snapshots = new ArrayList<>();
        NotionSprintDto sprint = notionClient.getSprint(notionPageId);
        snapshots.add(save(sprintId, jobId, SnapshotSource.NOTION_SPRINT, JSON_FILE.formatted(sprint.pageId()), sprint.pageId(), null, sprint.rawJson()));

        if (sprint.targetFeaturePageIds().isEmpty()) {
            throw new NonRetryableJobException("Notion 스프린트 '대상 기능'이 비어 있습니다. 대상 기능 없음");
        }

        List<SetupFeatureSpecDto> specs = new ArrayList<>();
        Map<String, List<WarningDto>> screenIdToWarnings = new HashMap<>();
        Set<String> screenIds = new HashSet<>();
        for (String pageId : sprint.targetFeaturePageIds()) {
            NotionSpecDto spec = notionClient.getSpec(pageId);
            NotionPageBodyDto body = notionClient.getPageBody(pageId);
            if (!screenIds.add(spec.screenId())) {
                throw new NonRetryableJobException("대상 기능의 화면 ID가 중복됩니다: " + spec.screenId());
            }

            List<Part> parts = toParts(spec);
            SpecStatus specStatus = toSpecStatus(spec);
            snapshots.add(save(sprintId, jobId, SnapshotSource.NOTION_SPEC, JSON_FILE.formatted(spec.pageId()), spec.pageId(), spec.screenId(), specSnapshot(spec, body)));
            if (specStatus == SpecStatus.NEEDS_CHECK) {
                screenIdToWarnings.put(spec.screenId(), List.of(WarningDto.of(WarningCode.SPEC_NEEDS_CHECK, "Notion 명세 상태가 '확인 필요'입니다")));
            }
            specs.add(SetupFeatureSpecDto.of(spec, parts, specStatus, body.markdown()));
        }

        String figmaFileKey = StringUtils.hasText(sprint.figmaQaPageUrl()) ? FigmaFileKeys.fromUrl(sprint.figmaQaPageUrl()) : null;
        return SetupNotionDto.of(sprint, figmaFileKey, specs, snapshots, screenIdToWarnings);
    }

    public SetupFigmaDto collectFigma(
            long sprintId,
            long jobId,
            String figmaFileKey,
            String sprintKey,
            List<SetupFeatureSpecDto> specs
    ) {
        if (figmaFileKey == null) {
            throw new NonRetryableJobException("Notion 스프린트 'Figma QA 페이지'가 비어 있어 Figma를 수집할 수 없습니다.");
        }

        FigmaQaFramesDto qaFrames = figmaClient.getQaFrames(figmaFileKey, sprintKey);
        qaFrames.warnings().forEach(warning -> log.warn("Figma QA 페이지 경고: {}", warning));
        String missingReason = missingReason(qaFrames, sprintKey);

        Map<String, List<FigmaScreenDto>> screenIdToScreens = new HashMap<>();
        Map<String, List<String>> screenIdToNodeIds = new HashMap<>();
        Map<String, List<Part>> screenIdToMissingParts = new HashMap<>();
        Map<String, List<WarningDto>> screenIdToWarnings = new HashMap<>();
        Map<String, FigmaPlatform> nodeIdToPlatform = new LinkedHashMap<>();
        Map<String, String> nodeIdToScreenId = new HashMap<>();
        for (SetupFeatureSpecDto spec : specs) {
            List<FigmaScreenDto> screens = new ArrayList<>();
            List<Part> missingParts = new ArrayList<>();
            List<WarningDto> warnings = new ArrayList<>();
            for (Part part : spec.parts()) {
                FigmaPlatform platform = PART_TO_PLATFORM.get(part);
                if (platform == null) {
                    continue;
                }
                Optional<FigmaScreenDto> screen = qaFrames.findScreen(platform, spec.screenId()).filter(found -> !found.frames().isEmpty());
                if (screen.isEmpty()) {
                    missingParts.add(part);
                    warnings.add(WarningDto.of(WarningCode.FIGMA_FRAME_MISSING, platform + " 프레임 없음" + (missingReason == null ? "" : " (" + missingReason + ")")));
                    continue;
                }
                screens.add(screen.get());
                for (FigmaFrameDto frame : screen.get().frames()) {
                    nodeIdToPlatform.put(frame.nodeId(), platform);
                    nodeIdToScreenId.put(frame.nodeId(), spec.screenId());
                }
            }

            screenIdToScreens.put(spec.screenId(), screens);
            screenIdToNodeIds.put(spec.screenId(), screens.stream().flatMap(screen -> screen.frames().stream()).map(FigmaFrameDto::nodeId).toList());
            screenIdToMissingParts.put(spec.screenId(), missingParts);
            screenIdToWarnings.put(spec.screenId(), warnings);
        }

        List<SetupSnapshotDto> snapshots = new ArrayList<>();
        if (!nodeIdToPlatform.isEmpty()) {
            FigmaFrameImagesDto images = figmaClient.exportPng(figmaFileKey, List.copyOf(nodeIdToPlatform.keySet()));
            if (!images.failedNodeIds().isEmpty()) {
                log.warn("Figma 프레임 PNG를 만들지 못했습니다: {}", images.failedNodeIds());
            }
            images.nodeIdToPng().forEach((nodeId, png) -> snapshots.add(save(
                    sprintId,
                    jobId,
                    SnapshotSource.FIGMA_FRAME,
                    FIGMA_FRAME_FILE.formatted(nodeIdToPlatform.get(nodeId), nodeId.replace(NODE_ID_SEPARATOR, FILE_NAME_SEPARATOR)),
                    nodeId,
                    nodeIdToScreenId.get(nodeId),
                    png
            )));
        }

        return SetupFigmaDto.of(screenIdToScreens, screenIdToNodeIds, screenIdToMissingParts, screenIdToWarnings, snapshots);
    }

    public SetupDiffDto collectDiff(
            long sprintId,
            long jobId,
            SetupBaselineDto baseline
    ) {
        Map<String, List<GithubChangedFileDto>> repoToFiles = new LinkedHashMap<>();
        List<SetupSnapshotDto> snapshots = new ArrayList<>();
        for (SetupBaselineRepoDto repo : baseline.repos()) {
            if (repo.previousCommitSha() == null) {
                continue;
            }
            List<GithubChangedFileDto> files = repo.previousCommitSha().equals(repo.commitSha())
                    ? List.of()
                    : githubClient.compare(githubRepoOf(repo.repo()), repo.previousCommitSha(), repo.commitSha()).files();

            repoToFiles.put(repo.repo(), files);
            snapshots.add(save(
                    sprintId,
                    jobId,
                    SnapshotSource.GITHUB_DIFF,
                    JSON_FILE.formatted(repo.repo()),
                    DIFF_SOURCE_REF.formatted(repo.repo(), repo.previousCommitSha(), repo.commitSha()),
                    null,
                    changedFilesSnapshot(files)
            ));
        }
        return SetupDiffDto.of(repoToFiles, snapshots);
    }

    public SetupOpenApiDto collectOpenApi(
            long sprintId,
            long jobId,
            List<SetupFeatureSpecDto> specs
    ) {
        OpenApiDocumentDto apiDocs = openApiClient.fetchApiDocs();
        SetupSnapshotDto snapshot = save(sprintId, jobId, SnapshotSource.OPENAPI, OPENAPI_FILE, apiDocs.url(), null, apiDocs.rawJson());

        Map<String, OpenApiExcerptDto> screenIdToExcerpt = new HashMap<>();
        Map<String, List<WarningDto>> screenIdToWarnings = new HashMap<>();
        for (SetupFeatureSpecDto spec : specs) {
            List<String> operationIds = spec.spec().relatedOperationIds();
            if (operationIds.isEmpty()) {
                continue;
            }
            OpenApiExcerptDto excerpt = openApiClient.extract(apiDocs, operationIds);
            screenIdToExcerpt.put(spec.screenId(), excerpt);
            if (!excerpt.missingOperationIds().isEmpty()) {
                screenIdToWarnings.put(spec.screenId(), List.of(WarningDto.of(
                        WarningCode.OPENAPI_OPERATION_MISSING,
                        "dev OpenAPI에 없는 operationId: " + String.join(DELIMITER, excerpt.missingOperationIds())
                )));
            }
        }
        return SetupOpenApiDto.of(screenIdToExcerpt, screenIdToWarnings, snapshot);
    }

    private List<Part> toParts(NotionSpecDto spec) {
        if (spec.parts().isEmpty()) {
            throw new NonRetryableJobException("기능 명세 '파트'가 비어 있습니다: " + spec.screenId());
        }
        return spec.parts().stream()
                .map(label -> Part.fromNotionLabel(label)
                        .orElseThrow(() -> new NonRetryableJobException("알 수 없는 Notion 파트 라벨 '" + label + "' (" + spec.screenId() + ")")))
                .distinct()
                .toList();
    }

    private SpecStatus toSpecStatus(NotionSpecDto spec) {
        if (!StringUtils.hasText(spec.specStatus())) {
            throw new NonRetryableJobException("기능 명세 '명세 상태'가 비어 있습니다: " + spec.screenId());
        }
        return SpecStatus.fromNotionLabel(spec.specStatus())
                .orElseThrow(() -> new NonRetryableJobException("알 수 없는 Notion 명세 상태 라벨 '" + spec.specStatus() + "' (" + spec.screenId() + ")"));
    }

    private String missingReason(
            FigmaQaFramesDto qaFrames,
            String sprintKey
    ) {
        for (FigmaFrameWarningDto warning : qaFrames.warnings()) {
            if (warning.type() == FigmaFrameWarningType.QA_PAGE_NOT_FOUND) {
                return "Figma 파일에 QA 페이지가 없음";
            }
            if (warning.type() == FigmaFrameWarningType.SPRINT_SECTION_NOT_FOUND) {
                return "QA 페이지에 '" + sprintKey + "' Section이 없음";
            }
        }
        return null;
    }

    private GithubRepo githubRepoOf(String repoName) {
        return Arrays.stream(GithubRepo.values())
                .filter(repo -> repo.getRepoName().equals(repoName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("알 수 없는 저장소입니다: " + repoName));
    }

    private String specSnapshot(
            NotionSpecDto spec,
            NotionPageBodyDto body
    ) {
        try {
            ObjectNode snapshot = objectMapper.createObjectNode();
            snapshot.set(PAGE_FIELD, objectMapper.readTree(spec.rawJson()));
            snapshot.put(MARKDOWN_FIELD, body.markdown());
            snapshot.set(BLOCKS_FIELD, objectMapper.readTree(body.rawJson()));
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Notion 기능 명세 스냅샷을 만들 수 없습니다: " + spec.screenId(), e);
        }
    }

    private String changedFilesSnapshot(List<GithubChangedFileDto> files) {
        ArrayNode array = objectMapper.createArrayNode();
        for (GithubChangedFileDto file : files) {
            array.addObject()
                    .put(FILENAME_FIELD, file.filename())
                    .put(STATUS_FIELD, file.status())
                    .put(ADDITIONS_FIELD, file.additions())
                    .put(DELETIONS_FIELD, file.deletions())
                    .put(PREVIOUS_FILENAME_FIELD, file.previousFilename());
        }
        return array.toString();
    }

    private SetupSnapshotDto save(
            long sprintId,
            long jobId,
            SnapshotSource source,
            String fileName,
            String sourceRef,
            String screenId,
            String content
    ) {
        return save(sprintId, jobId, source, fileName, sourceRef, screenId, content.getBytes(StandardCharsets.UTF_8));
    }

    private SetupSnapshotDto save(
            long sprintId,
            long jobId,
            SnapshotSource source,
            String fileName,
            String sourceRef,
            String screenId,
            byte[] content
    ) {
        return SetupSnapshotDto.of(
                source,
                sourceRef,
                screenId,
                snapshotFileStore.save(sprintId, jobId, source, fileName, content),
                KstTimes.now(clock)
        );
    }
}
