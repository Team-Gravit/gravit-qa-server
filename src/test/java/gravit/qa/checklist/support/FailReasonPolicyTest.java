package gravit.qa.checklist.support;

import gravit.qa.global.exception.domain.RestApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static gravit.qa.global.exception.domain.CustomErrorCode.INVALID_PARAMS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class FailReasonPolicyTest {

    @Nested
    @DisplayName("Fail 사유를 정리할 때")
    class 정리 {

        @Test
        void 앞뒤_공백을_지우고_비면_null로_본다() {
            // when & then
            assertSoftly(softly -> {
                softly.assertThat(FailReasonPolicy.normalize(null)).isNull();
                softly.assertThat(FailReasonPolicy.normalize("")).isNull();
                softly.assertThat(FailReasonPolicy.normalize(" \n\t ")).isNull();
                softly.assertThat(FailReasonPolicy.normalize("\u3000버튼 문구가 다르다  ")).isEqualTo("버튼 문구가 다르다");
            });
        }

        @Test
        void 앞뒤_공백을_지운_뒤_500자까지_받는다() {
            // given
            String reason = "가".repeat(FailReasonPolicy.MAX_LENGTH);

            // when & then
            assertSoftly(softly -> softly.assertThat(FailReasonPolicy.normalize("  " + reason + "  ")).isEqualTo(reason));
        }

        @Test
        void 앞뒤_공백을_지운_뒤_500자를_넘으면_GLOBAL_4001() {
            // when & then
            assertThatThrownBy(() -> FailReasonPolicy.normalize("가".repeat(FailReasonPolicy.MAX_LENGTH + 1)))
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(INVALID_PARAMS);
        }
    }
}
