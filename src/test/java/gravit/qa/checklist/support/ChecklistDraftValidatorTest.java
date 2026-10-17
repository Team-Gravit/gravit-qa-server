package gravit.qa.checklist.support;

import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.ChecklistDraftItemOutputDto;
import gravit.qa.checklist.dto.internal.ChecklistDraftOutputDto;
import gravit.qa.checklist.dto.internal.ChecklistItemDraftDto;
import gravit.qa.checklist.dto.internal.ClassificationCriteriaDto;
import gravit.qa.checklist.dto.internal.CriterionResultDto;
import gravit.qa.checklist.fixture.TestCatalogFixture;
import gravit.qa.feature.domain.Part;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static gravit.qa.checklist.fixture.TestCatalogFixture.LRN_EMPTY_STATE_TEST;
import static org.assertj.core.api.Assertions.assertThat;

class ChecklistDraftValidatorTest {

    private static final List<Part> FEATURE_PARTS = List.of(Part.SERVER, Part.WEB);

    private final ChecklistDraftValidator validator = new ChecklistDraftValidator(TestCatalogFixture.LRN_02_테스트가_있는_카탈로그());

    @Nested
    @DisplayName("LLM 출력을 의미 검증할 때")
    class 출력_검증 {

        @Test
        void 규칙을_지키면_오류가_없다() {
            // given
            ChecklistDraftOutputDto output = 출력(항목("근거", "제목", List.of(Part.WEB), "learner", LRN_EMPTY_STATE_TEST));

            // when & then
            assertThat(validator.validate(output, FEATURE_PARTS)).isEmpty();
        }

        @Test
        void 항목이_없으면_오류다() {
            // when & then
            assertThat(validator.validate(new ChecklistDraftOutputDto(List.of()), FEATURE_PARTS))
                    .containsExactly("items에 체크 항목이 1개 이상 있어야 합니다.");
        }

        @Test
        void 기능_파트에_없는_파트는_오류다() {
            // given
            ChecklistDraftOutputDto output = 출력(항목("근거", "제목", List.of(Part.WEB, Part.APP), null, null));

            // when & then
            assertThat(validator.validate(output, FEATURE_PARTS))
                    .containsExactly("items[0].parts의 APP는 기능 파트 [SERVER, WEB]에 없습니다.");
        }

        @Test
        void 파트가_비었거나_중복되면_오류다() {
            // given
            ChecklistDraftOutputDto output = 출력(
                    항목("근거", "제목", List.of(), null, null),
                    항목("근거", "제목", List.of(Part.WEB, Part.WEB), null, null)
            );

            // when & then
            assertThat(validator.validate(output, FEATURE_PARTS)).containsExactly(
                    "items[0].parts에 파트가 1개 이상 있어야 합니다.",
                    "items[1].parts에 같은 파트가 중복됩니다."
            );
        }

        @Test
        void 페르소나_목록에_없는_키는_오류다() {
            // given
            ChecklistDraftOutputDto output = 출력(항목("근거", "제목", List.of(Part.WEB), "newbie", null));

            // when & then
            assertThat(validator.validate(output, FEATURE_PARTS))
                    .containsExactly("items[0].personaKey 'newbie'는 페르소나 목록에 없습니다.");
        }

        @Test
        void 테스트_카탈로그에_없는_testRef는_오류다() {
            // given
            ChecklistDraftOutputDto output = 출력(항목("근거", "제목", List.of(Part.WEB), null, "LRN-02.unknown"));

            // when & then
            assertThat(validator.validate(output, FEATURE_PARTS))
                    .containsExactly("items[0].testRef 'LRN-02.unknown'는 테스트 카탈로그에 없습니다.");
        }

        @Test
        void 근거나_제목이_비어_있으면_오류다() {
            // given
            ChecklistDraftOutputDto output = 출력(항목(" ", "", List.of(Part.WEB), null, null));

            // when & then
            assertThat(validator.validate(output, FEATURE_PARTS)).containsExactly(
                    "items[0].evidence가 비어 있습니다.",
                    "items[0].title가 비어 있습니다."
            );
        }
    }

    @Nested
    @DisplayName("반영 전 초안을 재확인할 때")
    class 초안_재확인 {

        @Test
        void AUTO인데_testRef가_카탈로그에_없으면_오류다() {
            // given
            ChecklistItemDraftDto draft = ChecklistItemDraftDto.of("제목", "기대", List.of(Part.WEB), Classification.AUTO, null, false, null, null);

            // when & then
            assertThat(validator.validateDrafts(List.of(draft), FEATURE_PARTS))
                    .containsExactly("items[0]: AUTO 항목의 testRef가 테스트 카탈로그에 없습니다.");
        }

        @Test
        void 초안이_없으면_오류다() {
            // when & then
            assertThat(validator.validateDrafts(List.of(), FEATURE_PARTS))
                    .containsExactly("items에 체크 항목이 1개 이상 있어야 합니다.");
        }

        @Test
        void 규칙을_지킨_초안은_오류가_없다() {
            // given
            ChecklistItemDraftDto draft = ChecklistItemDraftDto.of("제목", "기대", List.of(Part.SERVER), Classification.AUTO, null, false, LRN_EMPTY_STATE_TEST, "learner");

            // when & then
            assertThat(validator.validateDrafts(List.of(draft), FEATURE_PARTS)).isEmpty();
        }
    }

    private static ChecklistDraftOutputDto 출력(ChecklistDraftItemOutputDto... items) {
        return new ChecklistDraftOutputDto(List.of(items));
    }

    private static ChecklistDraftItemOutputDto 항목(
            String evidence,
            String title,
            List<Part> parts,
            String personaKey,
            String testRef
    ) {
        CriterionResultDto met = new CriterionResultDto("근거", true);
        return new ChecklistDraftItemOutputDto(
                evidence,
                title,
                "기대 결과",
                parts,
                new ClassificationCriteriaDto(met, met, met, met, met),
                personaKey,
                testRef
        );
    }
}
