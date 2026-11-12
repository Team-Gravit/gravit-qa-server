package gravit.qa.fixReport.support;

import gravit.qa.checklist.dto.internal.CodeEvidenceDto;
import gravit.qa.checklist.dto.internal.FailRecordDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FailReasonSelectorTest {

    private static final CodeEvidenceDto OLD_EVIDENCE = CodeEvidenceDto.of("gravit-web/src/old.tsx", 1, 2);
    private static final CodeEvidenceDto NEW_EVIDENCE = CodeEvidenceDto.of("gravit-web/src/new.tsx", 3, 4);

    @Nested
    @DisplayName("사유를 고를 때")
    class 사유 {

        @Test
        void 지금_사유가_있으면_그것을_쓴다() {
            // when
            String reason = FailReasonSelector.reason("사람 사유", List.of(FailRecordDto.of("1차 사유", List.of())));

            // then
            assertThat(reason).isEqualTo("사람 사유");
        }

        @Test
        void 지금_사유가_없으면_가장_최근의_사유가_있는_FAIL_기록을_쓴다() {
            // given
            List<FailRecordDto> newestFirst = List.of(
                    FailRecordDto.of(null, List.of()),
                    FailRecordDto.of("  ", List.of()),
                    FailRecordDto.of("최근 1차 사유", List.of()),
                    FailRecordDto.of("예전 1차 사유", List.of())
            );

            // when
            String reason = FailReasonSelector.reason(null, newestFirst);

            // then
            assertThat(reason).isEqualTo("최근 1차 사유");
        }

        @Test
        void 어디에도_없으면_null이다() {
            // when
            String reason = FailReasonSelector.reason(" ", List.of(FailRecordDto.of(null, List.of())));

            // then
            assertThat(reason).isNull();
        }
    }

    @Nested
    @DisplayName("코드 근거를 고를 때")
    class 근거 {

        @Test
        void 지금_근거가_있으면_그것을_쓴다() {
            // when
            List<CodeEvidenceDto> evidence = FailReasonSelector.evidence(List.of(NEW_EVIDENCE), List.of(FailRecordDto.of("사유", List.of(OLD_EVIDENCE))));

            // then
            assertThat(evidence).containsExactly(NEW_EVIDENCE);
        }

        @Test
        void 지금_근거가_없으면_사유와_따로_가장_최근의_근거가_있는_FAIL_기록을_쓴다() {
            // given
            List<FailRecordDto> newestFirst = List.of(
                    FailRecordDto.of("사람 사유", List.of()),
                    FailRecordDto.of("1차 사유", List.of(NEW_EVIDENCE)),
                    FailRecordDto.of("예전 1차 사유", List.of(OLD_EVIDENCE))
            );

            // when
            List<CodeEvidenceDto> evidence = FailReasonSelector.evidence(List.of(), newestFirst);

            // then
            assertThat(evidence).containsExactly(NEW_EVIDENCE);
        }

        @Test
        void 어디에도_없으면_빈_목록이다() {
            // when
            List<CodeEvidenceDto> evidence = FailReasonSelector.evidence(List.of(), List.of(FailRecordDto.of("사유", List.of())));

            // then
            assertThat(evidence).isEmpty();
        }
    }
}
