package gravit.qa.checklist.support;

import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.ChecklistDraftItemOutputDto;
import gravit.qa.checklist.dto.internal.ChecklistDraftOutputDto;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecSection;
import gravit.qa.feature.dto.internal.SpecRowDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChecklistDraftValidatorTest {

    private static final List<Part> FEATURE_PARTS = List.of(Part.SERVER, Part.WEB, Part.APP);
    private static final List<String> SCREEN_COLUMNS = List.of("#", "요소", "내용", "언제", "근거");
    private static final List<SpecRowDto> ROWS = List.of(
            SpecRowDto.of("C1", SpecSection.SCREEN, SCREEN_COLUMNS, List.of("C1", "로그인 버튼", "[Google로 시작하기] [카카오로 시작하기]", "[웹]", "🎨")),
            SpecRowDto.of("C2", SpecSection.SCREEN, SCREEN_COLUMNS, List.of("C2", "로그인 버튼", "[Google로 시작하기]", "[앱]", "🎨")),
            SpecRowDto.of("B1", SpecSection.ACTION, List.of("#", "이렇게 하면", "이렇게 됩니다", "근거"), List.of("B1", "처음 가입하는 계정으로 로그인하면", "닉네임 설정으로 갑니다", "💬"))
    );

    @Nested
    @DisplayName("묶음 규칙에 맞으면")
    class 통과 {

        @Test
        void 모든_행을_다루고_파트가_행의_플랫폼과_맞으면_오류가_없다() {
            // given
            ChecklistDraftOutputDto output = new ChecklistDraftOutputDto(List.of(
                    항목("로그인 제공자 버튼 순서를 표시한다", List.of("C1"), List.of(Part.WEB)),
                    항목("앱 로그인 버튼을 표시한다", List.of("C2"), List.of(Part.APP)),
                    항목("처음 가입하면 닉네임 설정으로 간다", List.of("B1"), List.of(Part.SERVER, Part.WEB, Part.APP))
            ));

            // when & then
            assertThat(ChecklistDraftValidator.validate(output, FEATURE_PARTS, ROWS)).isEmpty();
        }

        @Test
        void 한_항목이_여러_행을_묶어도_된다() {
            // given
            ChecklistDraftOutputDto output = new ChecklistDraftOutputDto(List.of(
                    항목("로그인 버튼을 표시하고 가입 분기를 처리한다", List.of("C1", "C2", "B1"), List.of(Part.WEB, Part.APP))
            ));

            // when & then
            assertThat(ChecklistDraftValidator.validate(output, FEATURE_PARTS, ROWS)).isEmpty();
        }
    }

    @Nested
    @DisplayName("묶음 규칙을 어기면")
    class 오류 {

        @Test
        void 어느_항목도_다루지_않은_행을_알려준다() {
            // given
            ChecklistDraftOutputDto output = new ChecklistDraftOutputDto(List.of(항목("로그인 버튼", List.of("C1"), List.of(Part.WEB))));

            // when & then
            assertThat(ChecklistDraftValidator.validate(output, FEATURE_PARTS, ROWS))
                    .containsExactly("어느 항목도 다루지 않은 명세 행이 있습니다: C2, B1");
        }

        @Test
        void 명세에_없는_행_번호와_빈_행_번호를_막는다() {
            // given
            ChecklistDraftOutputDto output = new ChecklistDraftOutputDto(List.of(
                    항목("행위 하나", List.of("C9", "C1", "C2", "B1"), List.of(Part.WEB, Part.APP)),
                    항목("행위 둘", List.of(), List.of(Part.WEB))
            ));

            // when & then
            assertThat(ChecklistDraftValidator.validate(output, FEATURE_PARTS, ROWS)).containsExactly(
                    "items[0].specRowIds의 'C9'는 명세에 없는 행 번호입니다.",
                    "items[1].specRowIds에 다루는 명세 행 번호가 1개 이상 있어야 합니다."
            );
        }

        @Test
        void 웹_행만_다루는_항목에_APP을_넣으면_막는다() {
            // given
            ChecklistDraftOutputDto output = new ChecklistDraftOutputDto(List.of(
                    항목("로그인 버튼", List.of("C1"), List.of(Part.WEB, Part.APP)),
                    항목("나머지", List.of("C2", "B1"), List.of(Part.APP))
            ));

            // when & then
            assertThat(ChecklistDraftValidator.validate(output, FEATURE_PARTS, ROWS))
                    .containsExactly("items[0]: 다루는 명세 행이 모두 [웹] 행인데 parts에 APP이 있습니다.");
        }

        @Test
        void 같은_행위를_가진_항목을_둘_만들면_하나로_묶으라고_알려준다() {
            // given
            ChecklistDraftOutputDto output = new ChecklistDraftOutputDto(List.of(
                    항목("로그인 버튼을 표시한다", List.of("C1"), List.of(Part.WEB)),
                    항목("로그인 버튼을 표시한다", List.of("C2", "B1"), List.of(Part.APP))
            ));

            // when & then
            assertThat(ChecklistDraftValidator.validate(output, FEATURE_PARTS, ROWS))
                    .containsExactly("items[1].title '로그인 버튼을 표시한다'과 행위가 같은 항목이 이미 있습니다. 하나로 묶으세요.");
        }

        @Test
        void 기능_파트_밖의_파트와_빈_필드를_막는다() {
            // given
            ChecklistDraftItemOutputDto item = new ChecklistDraftItemOutputDto(" ", List.of("C1", "C2", "B1"), "행위", "", List.of(Part.SERVER), "", Classification.AUTO);

            // when
            List<String> errors = ChecklistDraftValidator.validate(new ChecklistDraftOutputDto(List.of(item)), List.of(Part.WEB, Part.APP), ROWS);

            // then
            assertThat(errors).containsExactly(
                    "items[0].evidence가 비어 있습니다.",
                    "items[0].expected가 비어 있습니다.",
                    "items[0].classificationReason가 비어 있습니다.",
                    "items[0].parts의 SERVER는 기능 파트 [WEB, APP]에 없습니다."
            );
        }

        @Test
        void 항목이_없으면_막는다() {
            // when & then
            assertThat(ChecklistDraftValidator.validate(new ChecklistDraftOutputDto(List.of()), FEATURE_PARTS, ROWS))
                    .containsExactly("items에 체크 항목이 1개 이상 있어야 합니다.");
        }
    }

    private static ChecklistDraftItemOutputDto 항목(
            String title,
            List<String> rowIds,
            List<Part> parts
    ) {
        return new ChecklistDraftItemOutputDto("근거", rowIds, title, title + " 확인", parts, "코드로 판정할 수 있다", Classification.AUTO);
    }
}
