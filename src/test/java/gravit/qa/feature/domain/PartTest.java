package gravit.qa.feature.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PartTest {

    @ParameterizedTest
    @CsvSource({"서버, SERVER", "웹, WEB", "앱, APP", "' 웹 ', WEB"})
    void Notion_파트_라벨을_파트로_바꾼다(
            String label,
            Part expected
    ) {
        // when & then
        assertThat(Part.fromNotionLabel(label)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"모바일", "WEB", "서버앱"})
    void 모르는_라벨은_빈_값이다(String label) {
        // when & then
        assertThat(Part.fromNotionLabel(label)).isEmpty();
    }

    @Test
    void 이름_목록과_서로_바꾼다() {
        // when
        List<String> names = Part.toNames(List.of(Part.WEB, Part.APP));

        // then
        assertThat(names).containsExactly("WEB", "APP");
        assertThat(Part.fromNames(names)).containsExactly(Part.WEB, Part.APP);
    }
}
