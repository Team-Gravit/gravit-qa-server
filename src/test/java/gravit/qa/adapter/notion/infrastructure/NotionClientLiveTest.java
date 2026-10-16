package gravit.qa.adapter.notion.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.config.AdapterRetryProps;
import gravit.qa.adapter.notion.config.NotionProps;
import gravit.qa.adapter.notion.dto.internal.NotionPageBodyDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecDto;
import gravit.qa.adapter.notion.dto.internal.NotionSprintDto;
import gravit.qa.adapter.support.ExternalCallExecutor;
import gravit.qa.adapter.support.ThreadSleeper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@Tag("live")
@EnabledIfEnvironmentVariable(named = "NOTION_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "QA_LIVE_NOTION_SPRINT_DB_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "QA_LIVE_NOTION_SPEC_DB_ID", matches = ".+")
class NotionClientLiveTest {

    @Test
    void 최근_스프린트와_대상_기능_명세_본문을_읽는다() {
        // given
        NotionClient notionClient = notionClient();

        // when
        List<NotionSprintDto> sprints = notionClient.findRecentSprints(1);
        assumeFalse(sprints.isEmpty(), "스프린트 DB에 행이 없습니다.");
        NotionSprintDto sprint = sprints.getFirst();
        List<NotionSpecDto> specs = notionClient.getTargetSpecs(sprint);

        // then
        assertThat(sprint.name()).isNotBlank();
        assertThat(specs).allSatisfy(spec -> assertThat(spec.screenId()).isNotBlank());
        if (!specs.isEmpty()) {
            NotionPageBodyDto body = notionClient.getPageBody(specs.getFirst().pageId());
            assertThat(body.markdown()).isNotNull();
        }
    }

    private static NotionClient notionClient() {
        NotionProps notionProps = new NotionProps(
                "https://api.notion.com",
                System.getenv("NOTION_TOKEN"),
                "2022-06-28",
                System.getenv("QA_LIVE_NOTION_SPRINT_DB_ID"),
                System.getenv("QA_LIVE_NOTION_SPEC_DB_ID"),
                Duration.ofSeconds(30)
        );
        ExternalCallExecutor executor = new ExternalCallExecutor(
                new ThreadSleeper(),
                new AdapterRetryProps(3, Duration.ofSeconds(1), Duration.ofSeconds(60))
        );
        return new NotionClient(RestClient.builder(), executor, Clock.systemDefaultZone(), new ObjectMapper(), notionProps);
    }
}
