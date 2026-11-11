package gravit.qa.sprint.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gravit.qa.adapter.exception.InvalidNotionSpecException;
import gravit.qa.adapter.notion.dto.internal.NotionPageBodyDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecDto;
import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;
import gravit.qa.adapter.notion.infrastructure.NotionClient;
import gravit.qa.feature.dto.internal.SpecPageDto;
import gravit.qa.feature.support.SpecPageParser;
import gravit.qa.global.util.KstTimes;
import gravit.qa.job.support.JobContext;
import gravit.qa.job.support.NonRetryableJobException;
import gravit.qa.snapshot.domain.SnapshotSource;
import gravit.qa.snapshot.infrastructure.SnapshotFileStore;
import gravit.qa.sprint.dto.internal.SetupSnapshotDto;
import gravit.qa.sprint.dto.internal.SetupTargetDto;
import gravit.qa.sprint.dto.internal.SpecCollectDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpecCollector {

    private static final String JSON_FILE = "%s.json";
    private static final String READ_FAILED_MESSAGE = "기능 명세를 읽지 못했습니다. 명세를 고친 뒤 준비를 다시 시작하세요. ";
    private static final String ERROR_LINE = "%s: %s";
    private static final String ERROR_DELIMITER = "; ";

    private final NotionClient notionClient;
    private final SnapshotFileStore snapshotFileStore;

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public SpecCollectDto collect(
            JobContext context,
            SetupTargetDto target
    ) {
        NotionSprintDto sprint = notionClient.getSprint(target.notionPageId());
        if (sprint.targetFeaturePageIds().isEmpty()) {
            throw new NonRetryableJobException("Notion 스프린트 '대상 기능'이 비어 있습니다");
        }

        List<SpecPageDto> pages = new ArrayList<>();
        List<SetupSnapshotDto> snapshots = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> screenIds = new HashSet<>();
        for (String pageId : sprint.targetFeaturePageIds()) {
            context.checkCancelled();
            NotionSpecDto spec = notionClient.getSpec(pageId);
            NotionPageBodyDto body = notionClient.getPageBody(pageId);
            if (!screenIds.add(spec.screenId())) {
                throw new NonRetryableJobException("대상 기능의 화면 ID가 중복됩니다: " + spec.screenId());
            }

            try {
                SpecPageDto page = SpecPageParser.parse(spec, body);
                pages.add(page);
                snapshots.add(snapshot(target.sprintId(), context.jobId(), spec, body, page, null));
            } catch (InvalidNotionSpecException e) {
                log.warn("기능 명세를 읽지 못했습니다. screenId={}, reason={}", spec.screenId(), e.getMessage());
                errors.add(ERROR_LINE.formatted(spec.screenId(), e.getMessage()));
                snapshots.add(snapshot(target.sprintId(), context.jobId(), spec, body, null, e.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            throw new NonRetryableJobException(READ_FAILED_MESSAGE + String.join(ERROR_DELIMITER, errors));
        }
        return new SpecCollectDto(pages, snapshots);
    }

    private SetupSnapshotDto snapshot(
            long sprintId,
            long jobId,
            NotionSpecDto spec,
            NotionPageBodyDto body,
            SpecPageDto page,
            String error
    ) {
        String content;
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.set("page", objectMapper.readTree(spec.rawJson()));
            node.set("table", page == null ? null : objectMapper.valueToTree(page.table()));
            node.set("specRows", page == null ? null : objectMapper.valueToTree(page.specRows()));
            node.set("history", page == null ? null : objectMapper.valueToTree(page.history()));
            node.put("markdown", page == null ? body.markdown() : page.specMarkdown());
            node.set("blocks", objectMapper.readTree(body.rawJson()));
            if (error != null) {
                node.put("error", error);
            }
            content = objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Notion 기능 명세 스냅샷을 만들 수 없습니다: " + spec.screenId(), e);
        }
        return SetupSnapshotDto.of(
                SnapshotSource.NOTION_SPEC,
                spec.pageId(),
                spec.screenId(),
                snapshotFileStore.save(sprintId, jobId, SnapshotSource.NOTION_SPEC, JSON_FILE.formatted(spec.pageId()), content),
                KstTimes.now(clock)
        );
    }
}
