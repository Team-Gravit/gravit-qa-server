package gravit.qa.sprint.support;

import gravit.qa.adapter.exception.BranchNotFoundException;
import gravit.qa.adapter.exception.ExternalApiException;
import gravit.qa.adapter.figma.domain.FigmaFrameWarningType;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameWarningDto;
import gravit.qa.adapter.figma.dto.internal.FigmaQaFramesDto;
import gravit.qa.adapter.figma.infrastructure.FigmaClient;
import gravit.qa.adapter.figma.support.FigmaFileKeys;
import gravit.qa.adapter.github.config.GithubProps;
import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.adapter.github.infrastructure.GithubClient;
import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;
import gravit.qa.adapter.notion.infrastructure.NotionClient;
import gravit.qa.global.util.KstTimes;
import gravit.qa.job.support.NonRetryableJobException;
import gravit.qa.snapshot.domain.SnapshotSource;
import gravit.qa.snapshot.infrastructure.SnapshotFileStore;
import gravit.qa.sprint.domain.ConnectionTarget;
import gravit.qa.sprint.dto.internal.ConnectResultDto;
import gravit.qa.sprint.dto.internal.ConnectionResultDto;
import gravit.qa.sprint.dto.internal.SetupSnapshotDto;
import gravit.qa.sprint.dto.internal.SetupTargetDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectCollector {

    public static final Map<ConnectionTarget, GithubRepo> TARGET_TO_REPO = new EnumMap<>(Map.of(
            ConnectionTarget.SERVER, GithubRepo.GRAVIT_SERVER,
            ConnectionTarget.WEB, GithubRepo.GRAVIT_WEB,
            ConnectionTarget.APP, GithubRepo.GRAVIT_AOS
    ));

    private static final String JSON_FILE = "%s.json";
    private static final String FIGMA_URL_EMPTY = "Notion 스프린트 'Figma QA 페이지'가 비어 있습니다";
    private static final String FIGMA_URL_INVALID = "Notion 'Figma QA 페이지' 값이 Figma 파일 URL이 아닙니다";
    private static final String QA_PAGE_MISSING = "Figma 파일에 QA 페이지가 없습니다";
    private static final String SECTION_MISSING = "QA 페이지에 '%s' Section이 없습니다";
    private static final String NOTION_UNREAD = "Notion을 읽지 못해 Figma 파일을 알 수 없습니다";

    private final NotionClient notionClient;
    private final FigmaClient figmaClient;
    private final GithubClient githubClient;
    private final SnapshotFileStore snapshotFileStore;

    private final Clock clock;
    private final GithubProps githubProps;

    public ConnectResultDto connect(
            long jobId,
            SetupTargetDto target
    ) {
        List<ConnectionResultDto> connections = new ArrayList<>();
        boolean retryable = false;

        NotionSprintDto notionSprint = null;
        SetupSnapshotDto snapshot = null;
        try {
            notionSprint = notionClient.getSprint(target.notionPageId());
            snapshot = SetupSnapshotDto.of(
                    SnapshotSource.NOTION_SPRINT,
                    notionSprint.pageId(),
                    null,
                    snapshotFileStore.save(target.sprintId(), jobId, SnapshotSource.NOTION_SPRINT, JSON_FILE.formatted(notionSprint.pageId()), notionSprint.rawJson()),
                    KstTimes.now(clock)
            );
            connections.add(ConnectionResultDto.connected(ConnectionTarget.NOTION));
        } catch (ExternalApiException e) {
            retryable = true;
            connections.add(ConnectionResultDto.failed(ConnectionTarget.NOTION, e.getMessage()));
        } catch (NonRetryableJobException e) {
            connections.add(ConnectionResultDto.failed(ConnectionTarget.NOTION, e.getMessage()));
        }

        String figmaFileKey = target.figmaFileKey();
        ConnectionResultDto figma;
        if (notionSprint == null) {
            figma = ConnectionResultDto.failed(ConnectionTarget.FIGMA, NOTION_UNREAD);
        } else if (!StringUtils.hasText(notionSprint.figmaQaPageUrl())) {
            figmaFileKey = null;
            figma = ConnectionResultDto.failed(ConnectionTarget.FIGMA, FIGMA_URL_EMPTY);
        } else {
            figmaFileKey = fileKeyOf(notionSprint.figmaQaPageUrl());
            if (figmaFileKey == null) {
                figma = ConnectionResultDto.failed(ConnectionTarget.FIGMA, FIGMA_URL_INVALID);
            } else {
                try {
                    figma = checkFigma(figmaFileKey, target.sprintKey());
                } catch (ExternalApiException e) {
                    retryable = true;
                    figma = ConnectionResultDto.failed(ConnectionTarget.FIGMA, e.getMessage());
                } catch (NonRetryableJobException e) {
                    figma = ConnectionResultDto.failed(ConnectionTarget.FIGMA, e.getMessage());
                }
            }
        }
        connections.add(figma);

        for (Map.Entry<ConnectionTarget, GithubRepo> entry : TARGET_TO_REPO.entrySet()) {
            try {
                String commitSha = githubClient.getBranchHeadSha(entry.getValue(), githubProps.qaBranch());
                connections.add(ConnectionResultDto.connected(entry.getKey(), githubProps.qaBranch(), commitSha));
            } catch (BranchNotFoundException e) {
                connections.add(ConnectionResultDto.failed(entry.getKey(), e.getMessage()));
            } catch (ExternalApiException e) {
                retryable = true;
                connections.add(ConnectionResultDto.failed(entry.getKey(), e.getMessage()));
            } catch (NonRetryableJobException e) {
                connections.add(ConnectionResultDto.failed(entry.getKey(), e.getMessage()));
            }
        }
        return new ConnectResultDto(notionSprint, figmaFileKey, connections, snapshot, retryable);
    }

    private ConnectionResultDto checkFigma(
            String figmaFileKey,
            String sprintKey
    ) {
        FigmaQaFramesDto frames = figmaClient.getQaFrames(figmaFileKey, sprintKey);
        for (FigmaFrameWarningDto warning : frames.warnings()) {
            if (warning.type() == FigmaFrameWarningType.QA_PAGE_NOT_FOUND) {
                return ConnectionResultDto.failed(ConnectionTarget.FIGMA, QA_PAGE_MISSING);
            }
            if (warning.type() == FigmaFrameWarningType.SPRINT_SECTION_NOT_FOUND) {
                return ConnectionResultDto.failed(ConnectionTarget.FIGMA, SECTION_MISSING.formatted(sprintKey));
            }
        }
        frames.warnings().forEach(warning -> log.warn("Figma QA 페이지 경고: {}", warning));
        return ConnectionResultDto.connected(ConnectionTarget.FIGMA);
    }

    private String fileKeyOf(String figmaUrl) {
        try {
            return FigmaFileKeys.fromUrl(figmaUrl);
        } catch (NonRetryableJobException e) {
            return null;
        }
    }
}
