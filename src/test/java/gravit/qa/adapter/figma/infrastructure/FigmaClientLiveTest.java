package gravit.qa.adapter.figma.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.config.AdapterRetryProps;
import gravit.qa.adapter.figma.config.FigmaProps;
import gravit.qa.adapter.figma.domain.FigmaPlatform;
import gravit.qa.adapter.figma.dto.internal.FigmaFrameWarningDto;
import gravit.qa.adapter.figma.dto.internal.FigmaQaFramesDto;
import gravit.qa.adapter.figma.dto.internal.FigmaScreenDto;
import gravit.qa.adapter.support.ExternalCallExecutor;
import gravit.qa.adapter.support.ThreadSleeper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;

import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.INVALID_FRAME_NAME;
import static gravit.qa.adapter.figma.domain.FigmaFrameWarningType.UNCONFIRMED_FRAME;
import static gravit.qa.adapter.figma.domain.FigmaPlatform.AOS;
import static gravit.qa.adapter.figma.domain.FigmaPlatform.WEB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

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

    @Test
    @EnabledIfEnvironmentVariable(named = "QA_LIVE_FIGMA_SPRINT_KEY", matches = ".+")
    void 스프린트_Section을_2026_10_17_적용_결과대로_해석한다() {
        // given
        FigmaClient figmaClient = figmaClient();

        // when
        FigmaQaFramesDto result = figmaClient.getQaFrames(System.getenv("QA_LIVE_FIGMA_FILE_KEY"), System.getenv("QA_LIVE_FIGMA_SPRINT_KEY"));

        // then
        assertSoftly(softly -> {
            softly.assertThat(result.sprintSectionId()).as("스프린트 Section").isNotNull();
            softly.assertThat(frameCount(result, WEB)).as("WEB 화면 프레임").isEqualTo(119);
            softly.assertThat(frameCount(result, AOS)).as("AOS 화면 프레임").isEqualTo(109);
            softly.assertThat(screenCount(result, WEB)).as("WEB 화면").isEqualTo(49);
            softly.assertThat(screenCount(result, AOS)).as("AOS 화면").isEqualTo(48);
            softly.assertThat(result.warnings())
                    .as("미확인 프레임")
                    .filteredOn(warning -> warning.type() == UNCONFIRMED_FRAME)
                    .extracting(FigmaFrameWarningDto::platform)
                    .containsExactlyInAnyOrder(WEB, WEB, WEB, WEB, WEB, AOS);
            softly.assertThat(result.warnings())
                    .as("규칙 밖 이름")
                    .filteredOn(warning -> warning.type() == INVALID_FRAME_NAME)
                    .extracting(FigmaFrameWarningDto::platform, FigmaFrameWarningDto::name)
                    .containsExactly(tuple(WEB, "최근 응시 이력 페이지"), tuple(WEB, "최근 응시 이력 페이지"));
            softly.assertThat(result.warnings())
                    .as("그 외 경고")
                    .filteredOn(warning -> !Set.of(UNCONFIRMED_FRAME, INVALID_FRAME_NAME).contains(warning.type()))
                    .isEmpty();
        });
    }

    private static long frameCount(
            FigmaQaFramesDto result,
            FigmaPlatform platform
    ) {
        return result.screens().stream()
                .filter(screen -> screen.platform() == platform)
                .mapToLong(screen -> screen.frames().size())
                .sum();
    }

    private static long screenCount(
            FigmaQaFramesDto result,
            FigmaPlatform platform
    ) {
        return result.screens().stream()
                .map(FigmaScreenDto::platform)
                .filter(platform::equals)
                .count();
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
