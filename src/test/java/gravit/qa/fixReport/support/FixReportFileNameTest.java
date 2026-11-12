package gravit.qa.fixReport.support;

import gravit.qa.sprint.domain.ProgressPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class FixReportFileNameTest {

    @Nested
    @DisplayName("파일 이름을 만들 때")
    class 파일_이름 {

        @Test
        void 스프린트_이름을_slug로_바꾸고_파트를_붙인다() {
            // when & then
            assertSoftly(softly -> {
                softly.assertThat(FixReportFileName.of(1L, "Test Agile", ProgressPart.SERVER)).isEqualTo("test-agile_SERVER_fixes.txt");
                softly.assertThat(FixReportFileName.of(1L, "3rd Agile", ProgressPart.DESIGN)).isEqualTo("3rd-agile_DESIGN_fixes.txt");
            });
        }

        @Test
        void 영문_숫자가_아닌_글자가_이어지면_하이픈_하나로_바꾸고_앞뒤_하이픈을_지운다() {
            // when & then
            assertSoftly(softly -> {
                softly.assertThat(FixReportFileName.slug(1L, "  --Sprint #12 (QA)!! ")).isEqualTo("sprint-12-qa");
                softly.assertThat(FixReportFileName.slug(1L, "3차 애자일")).isEqualTo("3");
            });
        }

        @Test
        void 결과가_비면_스프린트_ID를_쓴다() {
            // when & then
            assertThat(FixReportFileName.of(7L, "테스트 애자일", ProgressPart.WEB)).isEqualTo("sprint-7_WEB_fixes.txt");
        }
    }

    @Nested
    @DisplayName("Content-Disposition 값을 만들 때")
    class 첨부_헤더 {

        @Test
        void filename과_UTF_8_filename_별표를_함께_쓴다() {
            // when & then
            assertThat(FixReportFileName.contentDisposition("test-agile_SERVER_fixes.txt"))
                    .isEqualTo("attachment; filename=\"test-agile_SERVER_fixes.txt\"; filename*=UTF-8''test-agile_SERVER_fixes.txt");
        }
    }
}
