package gravit.qa.feature.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SpecStatusTest {

    @ParameterizedTest
    @CsvSource({"역문서화 초안, DRAFT", "확인 필요, NEEDS_CHECK", "확정, CONFIRMED"})
    void Notion_명세_상태_라벨을_상태로_바꾼다(
            String label,
            SpecStatus expected
    ) {
        // when & then
        assertThat(SpecStatus.fromNotionLabel(label)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"초안", "CONFIRMED", "확인필요"})
    void 모르는_라벨은_빈_값이다(String label) {
        // when & then
        assertThat(SpecStatus.fromNotionLabel(label)).isEmpty();
    }
}
