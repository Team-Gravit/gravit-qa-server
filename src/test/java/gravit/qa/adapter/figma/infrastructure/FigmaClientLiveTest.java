package gravit.qa.adapter.figma.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.config.AdapterRetryProps;
import gravit.qa.adapter.figma.config.FigmaProps;
import gravit.qa.adapter.figma.dto.internal.FigmaQaFramesDto;
import gravit.qa.adapter.support.ExternalCallExecutor;
import gravit.qa.adapter.support.ThreadSleeper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("live")
@EnabledIfEnvironmentVariable(named = "FIGMA_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "QA_LIVE_FIGMA_FILE_KEY", matches = ".+")
class FigmaClientLiveTest {

    @Test
    void QA_페이지를_찾아_스프린트_Section을_해석한다() {
        // given
        FigmaClient figmaClient = figmaClient();

        // when
        FigmaQaFramesDto result = figmaClient.getQaFrames(System.getenv("QA_LIVE_FIGMA_FILE_KEY"), "");

        // then
        assertThat(result.qaPageId()).as("QA 페이지가 있어야 합니다").isNotNull();
    }

    private static FigmaClient figmaClient() {
        FigmaProps figmaProps = new FigmaProps("https://api.figma.com", System.getenv("FIGMA_TOKEN"), Duration.ofSeconds(30));
        ExternalCallExecutor executor = new ExternalCallExecutor(
                new ThreadSleeper(),
                new AdapterRetryProps(3, Duration.ofSeconds(1), Duration.ofSeconds(60))
        );
        return new FigmaClient(RestClient.builder(), executor, Clock.systemDefaultZone(), new ObjectMapper(), figmaProps);
    }
}
