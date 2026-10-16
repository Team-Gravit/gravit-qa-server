package gravit.qa.adapter.github.support;

import gravit.qa.adapter.github.dto.internal.AosVersionDto;
import gravit.qa.job.support.NonRetryableJobException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class AosVersionParserTest {

    private static final String BUILD_GRADLE = """
            android {
                namespace = "com.gravit.app"
                defaultConfig {
                    applicationId = "com.gravit.app"
                    minSdk = 26
                    targetSdk = 35
                    // versionCode = 99
                    versionCode = 1
                    versionName = "1.0.0"
                }
            }
            """;

    @Nested
    @DisplayName("build.gradle.kts에서 버전을 추출할 때")
    class 추출 {

        @Test
        void 주석이_아닌_versionCode와_versionName을_읽는다() {
            // when
            AosVersionDto version = AosVersionParser.parse(BUILD_GRADLE, null);

            // then
            assertSoftly(softly -> {
                softly.assertThat(version.versionCode()).isEqualTo(1);
                softly.assertThat(version.versionName()).isEqualTo("1.0.0");
                softly.assertThat(version.previousVersionCode()).isNull();
                softly.assertThat(version.versionCodeWarning()).isFalse();
            });
        }

        @Test
        void versionCode가_없으면_재시도하지_않는_실패() {
            // when & then
            assertThatThrownBy(() -> AosVersionParser.parse("versionName = \"1.0.0\"", null))
                    .isInstanceOf(NonRetryableJobException.class)
                    .hasMessageContaining("versionCode");
        }

        @Test
        void versionName이_없으면_재시도하지_않는_실패() {
            // when & then
            assertThatThrownBy(() -> AosVersionParser.parse("versionCode = 3", null))
                    .isInstanceOf(NonRetryableJobException.class)
                    .hasMessageContaining("versionName");
        }
    }

    @Nested
    @DisplayName("직전 versionCode와 비교할 때")
    class 증가_검사 {

        @Test
        void 직전보다_크면_경고하지_않는다() {
            // when
            AosVersionDto version = AosVersionParser.parse(BUILD_GRADLE.replace("versionCode = 1", "versionCode = 2"), 1);

            // then
            assertThat(version.versionCodeWarning()).isFalse();
        }

        @Test
        void 직전과_같으면_경고한다() {
            // when
            AosVersionDto version = AosVersionParser.parse(BUILD_GRADLE, 1);

            // then
            assertThat(version.versionCodeWarning()).isTrue();
        }

        @Test
        void 직전보다_작으면_경고한다() {
            // when
            AosVersionDto version = AosVersionParser.parse(BUILD_GRADLE, 5);

            // then
            assertThat(version.versionCodeWarning()).isTrue();
        }
    }
}
