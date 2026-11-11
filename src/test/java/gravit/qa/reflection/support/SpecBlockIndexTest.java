package gravit.qa.reflection.support;

import gravit.qa.adapter.notion.dto.internal.NotionBlockDto;
import gravit.qa.adapter.notion.dto.internal.NotionHistoryTableDto;
import gravit.qa.adapter.notion.support.NotionBlockRenderer;
import gravit.qa.adapter.notion.support.NotionHistoryTableParser;
import gravit.qa.reflection.domain.ReflectionOperationType;
import gravit.qa.reflection.dto.internal.ReflectionOperationDto;
import gravit.qa.reflection.dto.internal.ReflectionOperationOutputDto;
import gravit.qa.reflection.dto.internal.ReflectionOutputDto;
import gravit.qa.reflection.dto.internal.ReflectionSkippedRowOutputDto;
import gravit.qa.reflection.dto.internal.SpecBlockEntryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.글자;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.문단;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.제목3;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.표_블록;
import static gravit.qa.adapter.notion.fixture.NotionSpecPageFixture.표_행;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class SpecBlockIndexTest {

    private static final String PAGE_ID = "11111111111111111111111111111111";
    private static final String BASIC_TABLE_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaa0001";
    private static final String HEADING_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaa0002";
    private static final String SCREEN_TABLE_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaa0003";
    private static final String C1_ROW_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaa0005";
    private static final String NOTE_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaa0006";
    private static final String HISTORY_HEADING_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaa0007";
    private static final String HISTORY_TABLE_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaa0008";

    private final List<NotionBlockDto> blocks = List.of(
            new NotionBlockDto(표_블록(BASIC_TABLE_ID), List.of(
                    new NotionBlockDto(표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0010", "항목", "값"), List.of()),
                    new NotionBlockDto(표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0011", "파트", "웹"), List.of())
            )),
            new NotionBlockDto(제목3(HEADING_ID, "화면에 보이는 것"), List.of()),
            new NotionBlockDto(표_블록(SCREEN_TABLE_ID, 3), List.of(
                    new NotionBlockDto(표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0004", "#", "요소", "내용"), List.of()),
                    new NotionBlockDto(표_행(C1_ROW_ID, "C1", "로그인 버튼", "구글, 카카오"), List.of())
            )),
            new NotionBlockDto(문단(NOTE_ID, 글자("참고")), List.of()),
            new NotionBlockDto(제목3(HISTORY_HEADING_ID, "수정 이력"), List.of()),
            new NotionBlockDto(표_블록(HISTORY_TABLE_ID, 3), List.of(
                    new NotionBlockDto(표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0012", "스프린트", "수정 사항", "반영일"), List.of()),
                    new NotionBlockDto(표_행("aaaaaaaaaaaaaaaaaaaaaaaaaaaa0013", "4th Agile", "Apple 버튼 추가", ""), List.of())
            ))
    );
    private final NotionHistoryTableDto history = NotionHistoryTableParser.parse(blocks);
    private final SpecBlockIndex index = SpecBlockIndex.of(PAGE_ID, blocks, history);

    @Nested
    @DisplayName("본문 블록 목록을 만들 때")
    class 목록 {

        @Test
        void 블록과_표_행에_키를_붙이고_수정_이력_절은_목록에서_뺀다() {
            // when
            String rendered = index.render();

            // then
            assertSoftly(softly -> {
                softly.assertThat(rendered).contains("[t1] table (열 2개) 명세 맨 위 항목, 값 표: 값 칸만 수정, 행 추가 금지");
                softly.assertThat(rendered).contains("[t2.r1] (머리글, 수정 금지) # | 요소 | 내용");
                softly.assertThat(rendered).contains("[t2.r2] C1 | 로그인 버튼 | 구글, 카카오");
                softly.assertThat(rendered).contains("[b2] paragraph: 참고");
                softly.assertThat(rendered).doesNotContain("수정 이력").doesNotContain("Apple 버튼 추가");
            });
            SpecBlockEntryDto c1 = index.find("t2.r2").orElseThrow();
            assertThat(c1.blockId()).isEqualTo(C1_ROW_ID);
            assertThat(c1.parentId()).isEqualTo(SCREEN_TABLE_ID);
            assertThat(c1.tableWidth()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("편집을 적용해 바뀐 뒤 본문을 만들 때")
    class 적용 {

        @Test
        void 행_수정과_같은_대상_뒤_추가를_적힌_순서대로_반영하고_원본은_그대로_둔다() {
            // given
            List<ReflectionOperationDto> operations = List.of(
                    ReflectionOperationDto.of(1, ReflectionOperationType.UPDATE_ROW, "t2.r2", C1_ROW_ID, null, "table_row", null, List.of("C1", "로그인 버튼", "구글, 카카오, Apple"), 3),
                    ReflectionOperationDto.of(1, ReflectionOperationType.INSERT_ROW_AFTER, "t2.r2", C1_ROW_ID, SCREEN_TABLE_ID, "table_row", null, List.of("C2", "안내", "문구"), 3),
                    ReflectionOperationDto.of(1, ReflectionOperationType.INSERT_BLOCK_AFTER, "b2", NOTE_ID, PAGE_ID, "paragraph", "첫 문단", null, null),
                    ReflectionOperationDto.of(1, ReflectionOperationType.INSERT_BLOCK_AFTER, "b2", NOTE_ID, PAGE_ID, "paragraph", "둘째 문단", null, null)
            );

            // when
            String after = NotionBlockRenderer.toMarkdown(index.apply(operations));
            String before = NotionBlockRenderer.toMarkdown(blocks);

            // then
            assertSoftly(softly -> {
                softly.assertThat(after).contains("| C1 | 로그인 버튼 | 구글, 카카오, Apple |\n| C2 | 안내 | 문구 |");
                softly.assertThat(after).contains("참고\n첫 문단\n둘째 문단");
                softly.assertThat(before).contains("| C1 | 로그인 버튼 | 구글, 카카오 |");
                softly.assertThat(before).doesNotContain("첫 문단");
            });
        }

        @Test
        void 삭제한_블록은_바뀐_뒤_본문에서_빠진다() {
            // given
            List<ReflectionOperationDto> operations = List.of(
                    ReflectionOperationDto.of(1, ReflectionOperationType.DELETE, "b2", NOTE_ID, null, "paragraph", null, null, null)
            );

            // when & then
            assertThat(NotionBlockRenderer.toMarkdown(index.apply(operations))).doesNotContain("참고");
        }
    }

    @Nested
    @DisplayName("LLM 편집 목록을 검증할 때")
    class 검증 {

        @Test
        void 키가_맞고_모든_이력_행을_다루면_오류가_없다() {
            // given
            ReflectionOutputDto output = new ReflectionOutputDto("근거", List.of(
                    new ReflectionOperationOutputDto(1, ReflectionOperationType.UPDATE_ROW, "t2.r2", null, null, List.of("C1", "로그인 버튼", "구글, 카카오, Apple"))
            ), List.of(new ReflectionSkippedRowOutputDto(2, "이미 반영됨")));

            // when & then
            assertThat(ReflectionValidator.validate(output, index, 2)).isEmpty();
        }

        @Test
        void 머리글과_항목_이름_칸과_열_수와_없는_키와_다루지_않은_이력_행을_막는다() {
            // given
            ReflectionOutputDto output = new ReflectionOutputDto("근거", List.of(
                    new ReflectionOperationOutputDto(1, ReflectionOperationType.UPDATE_ROW, "t2.r1", null, null, List.of("#", "요소", "내용")),
                    new ReflectionOperationOutputDto(1, ReflectionOperationType.UPDATE_ROW, "t1.r2", null, null, List.of("플랫폼", "웹")),
                    new ReflectionOperationOutputDto(1, ReflectionOperationType.INSERT_ROW_AFTER, "t2.r2", null, null, List.of("C2")),
                    new ReflectionOperationOutputDto(1, ReflectionOperationType.UPDATE_TEXT, "b9", null, "x", null)
            ), List.of());

            // when
            List<String> errors = ReflectionValidator.validate(output, index, 2);

            // then
            assertThat(errors).containsExactly(
                    "operations[0]: UPDATE_ROW는 머리글이 아닌 표 행에만 씁니다.",
                    "operations[1]: 명세 맨 위 항목, 값 표의 첫 칸(항목 이름)은 바꿀 수 없습니다.",
                    "operations[2].cells는 그 표의 열 수(3개)만큼 있어야 합니다.",
                    "operations[3].target 'b9'는 본문 블록 목록에 없는 키입니다.",
                    "편집이나 skippedRows로 다루지 않은 수정 이력 행이 있습니다: 2"
            );
        }

        @Test
        void 같은_대상을_두_번_바꾸면_합치라고_알려준다() {
            // given
            ReflectionOutputDto output = new ReflectionOutputDto("근거", List.of(
                    new ReflectionOperationOutputDto(1, ReflectionOperationType.UPDATE_TEXT, "b2", null, "하나", null),
                    new ReflectionOperationOutputDto(1, ReflectionOperationType.DELETE, "b2", null, null, null)
            ), List.of());

            // when & then
            assertThat(ReflectionValidator.validate(output, index, 1))
                    .containsExactly("operations[1].target 'b2'를 두 번 이상 바꾸거나 지웁니다. 한 편집으로 합치세요.");
        }
    }
}
