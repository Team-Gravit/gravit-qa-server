package gravit.qa.sprint.domain;

import gravit.qa.global.exception.domain.RestApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_STATUS_CONFLICT;
import static gravit.qa.global.exception.domain.CustomErrorCode.VERSION_CONFLICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SprintTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 0);

    @Nested
    @DisplayName("준비 흐름을 따라갈 때")
    class 준비_흐름 {

        @Test
        void DRAFT에서_명세_로드를_시작하고_단계마다_다음_상태로_넘어가_MANUAL_REVIEW가_된다() {
            // given
            Sprint sprint = 스프린트(SprintStatus.DRAFT);

            // when
            sprint.startSpecLoad();
            SprintStatus afterStart = sprint.getStatus();
            sprint.advanceFrom(SprintStatus.SPEC_LOAD);
            SprintStatus afterSpec = sprint.getStatus();
            sprint.advanceFrom(SprintStatus.CHECKLIST);
            sprint.advanceFrom(SprintStatus.DESIGN_REVIEW);
            sprint.advanceFrom(SprintStatus.AUTO_REVIEW);

            // then
            assertThat(afterStart).isEqualTo(SprintStatus.SPEC_LOAD);
            assertThat(afterSpec).isEqualTo(SprintStatus.CHECKLIST);
            assertThat(sprint.getStatus()).isEqualTo(SprintStatus.MANUAL_REVIEW);
        }

        @ParameterizedTest
        @EnumSource(value = SprintStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
        void DRAFT가_아니면_명세_로드를_시작할_수_없다(SprintStatus status) {
            // given
            Sprint sprint = 스프린트(status);

            // when & then
            assertThatThrownBy(sprint::startSpecLoad)
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(SPRINT_STATUS_CONFLICT);
        }

        @Test
        void 지금_상태가_아닌_단계에서_넘기려_하면_막는다() {
            // given
            Sprint sprint = 스프린트(SprintStatus.CHECKLIST);

            // when & then
            assertThatThrownBy(() -> sprint.advanceFrom(SprintStatus.DESIGN_REVIEW)).isInstanceOf(IllegalStateException.class);
            assertThat(sprint.getStatus()).isEqualTo(SprintStatus.CHECKLIST);
        }

        @ParameterizedTest
        @EnumSource(value = SprintStatus.class, names = {"MANUAL_REVIEW", "CLOSED", "DRAFT"})
        void MANUAL_REVIEW_이후와_DRAFT에서는_다음_준비_단계가_없다(SprintStatus status) {
            // given
            Sprint sprint = 스프린트(status);

            // when & then
            assertThatThrownBy(() -> sprint.advanceFrom(status)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("종료할 때")
    class 종료 {

        @Test
        void MANUAL_REVIEW에서_버전이_맞으면_CLOSED가_된다() {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);

            // when
            sprint.close(0, NOW);

            // then
            assertThat(sprint.getStatus()).isEqualTo(SprintStatus.CLOSED);
            assertThat(sprint.getClosedAt()).isEqualTo(NOW);
        }

        @Test
        void MANUAL_REVIEW가_아니면_SPRINT_STATUS_CONFLICT() {
            // given
            Sprint sprint = 스프린트(SprintStatus.AUTO_REVIEW);

            // when & then
            assertThatThrownBy(() -> sprint.close(0, NOW))
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(SPRINT_STATUS_CONFLICT);
        }

        @Test
        void 버전이_다르면_VERSION_CONFLICT() {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);

            // when & then
            assertThatThrownBy(() -> sprint.close(4, NOW))
                    .isInstanceOf(RestApiException.class)
                    .extracting(e -> ((RestApiException) e).getErrorCode())
                    .isEqualTo(VERSION_CONFLICT);
        }
    }

    @Test
    void QA_준비_작업_상태는_CHECKLIST부터_AUTO_REVIEW까지이고_항목_편집은_체크리스트_생성_뒤부터다() {
        // when & then
        assertThat(SprintStatus.CHECKLIST.isQaPreparing()).isTrue();
        assertThat(SprintStatus.AUTO_REVIEW.isQaPreparing()).isTrue();
        assertThat(SprintStatus.MANUAL_REVIEW.isQaPreparing()).isFalse();
        assertThat(SprintStatus.CHECKLIST.allowsChecklistEdit()).isFalse();
        assertThat(SprintStatus.DESIGN_REVIEW.allowsChecklistEdit()).isTrue();
        assertThat(SprintStatus.MANUAL_REVIEW.allowsChecklistEdit()).isTrue();
        assertThat(SprintStatus.CLOSED.allowsChecklistEdit()).isFalse();
    }

    private Sprint 스프린트(SprintStatus status) {
        Sprint sprint = Sprint.create("4th Agile", "11111111111111111111111111111111", "요약", "AbC123", 7L);
        ReflectionTestUtils.setField(sprint, "status", status);
        return sprint;
    }
}
