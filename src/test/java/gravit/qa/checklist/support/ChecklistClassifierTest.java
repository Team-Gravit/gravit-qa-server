package gravit.qa.checklist.support;

import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.ClassificationCriteriaDto;
import gravit.qa.checklist.dto.internal.ClassificationResultDto;
import gravit.qa.checklist.dto.internal.CriterionResultDto;
import gravit.qa.checklist.fixture.TestCatalogFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static gravit.qa.checklist.fixture.TestCatalogFixture.LRN_EMPTY_STATE_TEST;
import static org.assertj.core.api.Assertions.assertThat;

class ChecklistClassifierTest {

    private final ChecklistClassifier classifier = new ChecklistClassifier(TestCatalogFixture.LRN_02_테스트가_있는_카탈로그());

    @Nested
    @DisplayName("기준 5개가 모두 충족될 때")
    class 기준_모두_충족 {

        @Test
        void 카탈로그에_있는_testRef면_AUTO() {
            // when
            ClassificationResultDto result = classifier.classify(기준(true), LRN_EMPTY_STATE_TEST);

            // then
            assertThat(result).isEqualTo(ClassificationResultDto.of(Classification.AUTO, false));
        }

        @Test
        void testRef가_없으면_MANUAL이고_자동화_후보다() {
            // when
            ClassificationResultDto result = classifier.classify(기준(true), null);

            // then
            assertThat(result).isEqualTo(ClassificationResultDto.of(Classification.MANUAL, true));
        }

        @Test
        void 카탈로그에_없는_testRef면_MANUAL이고_자동화_후보다() {
            // when
            ClassificationResultDto result = classifier.classify(기준(true), "LRN-02.unknown");

            // then
            assertThat(result).isEqualTo(ClassificationResultDto.of(Classification.MANUAL, true));
        }
    }

    @Nested
    @DisplayName("기준이 하나라도 미충족이면")
    class 기준_미충족 {

        @Test
        void 카탈로그에_있는_testRef여도_MANUAL이고_자동화_후보가_아니다() {
            // when
            ClassificationResultDto result = classifier.classify(기준(false), LRN_EMPTY_STATE_TEST);

            // then
            assertThat(result).isEqualTo(ClassificationResultDto.of(Classification.MANUAL, false));
        }

        @Test
        void 기준이_없으면_MANUAL이다() {
            // when
            ClassificationResultDto result = classifier.classify(null, LRN_EMPTY_STATE_TEST);

            // then
            assertThat(result).isEqualTo(ClassificationResultDto.of(Classification.MANUAL, false));
        }
    }

    private static ClassificationCriteriaDto 기준(boolean observable) {
        CriterionResultDto met = new CriterionResultDto("근거", true);
        return new ClassificationCriteriaDto(met, met, new CriterionResultDto("화면 관찰", observable), met, met);
    }
}
