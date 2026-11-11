package gravit.qa.designReview.support;

import gravit.qa.checklist.domain.Verdict;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.designReview.dto.internal.DesignReviewItemOutputDto;
import gravit.qa.designReview.dto.internal.DesignReviewOutputDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DesignReviewValidatorTest {

    private static final Map<DesignPlatform, List<String>> PLATFORM_TO_ROWS = Map.of(DesignPlatform.WEB, List.of("C1", "C2", "C3"));
    private static final Map<DesignPlatform, Set<String>> PLATFORM_TO_NODES = Map.of(DesignPlatform.WEB, Set.of("4:2", "4:10"));

    @Nested
    @DisplayName("화면 영역 묶음이 맞으면")
    class 통과 {

        @Test
        void 플랫폼의_C행을_한_번씩_다루면_오류가_없다() {
            // given
            DesignReviewOutputDto output = new DesignReviewOutputDto(List.of(
                    항목(DesignPlatform.WEB, List.of("C1", "C2"), List.of("4:10"), Verdict.PASS),
                    항목(DesignPlatform.WEB, List.of("C3"), List.of(), Verdict.UNKNOWN)
            ));

            // when & then
            assertThat(DesignReviewValidator.validate(output, PLATFORM_TO_ROWS, PLATFORM_TO_NODES)).isEmpty();
        }
    }

    @Nested
    @DisplayName("묶음이 어긋나면")
    class 오류 {

        @Test
        void 행이_두_항목에_들어가거나_빠지면_막는다() {
            // given
            DesignReviewOutputDto output = new DesignReviewOutputDto(List.of(
                    항목(DesignPlatform.WEB, List.of("C1"), List.of("4:2"), Verdict.PASS),
                    항목(DesignPlatform.WEB, List.of("C1"), List.of("4:2"), Verdict.FAIL)
            ));

            // when & then
            assertThat(DesignReviewValidator.validate(output, PLATFORM_TO_ROWS, PLATFORM_TO_NODES)).containsExactly(
                    "items[1].specRowIds의 'C1'가 WEB의 다른 항목에도 있습니다. 행은 한 항목에만 넣습니다.",
                    "WEB에서 어느 항목도 다루지 않은 C행이 있습니다: C2, C3"
            );
        }

        @Test
        void 입력에_없는_플랫폼과_레이어와_근거_없는_PASS를_막는다() {
            // given
            DesignReviewOutputDto output = new DesignReviewOutputDto(List.of(
                    항목(DesignPlatform.APP, List.of("C1"), List.of(), Verdict.PASS),
                    항목(DesignPlatform.WEB, List.of("C1", "C2", "C3"), List.of("9:9"), Verdict.FAIL),
                    항목(DesignPlatform.WEB, List.of("C4"), List.of(), Verdict.PASS)
            ));

            // when & then
            assertThat(DesignReviewValidator.validate(output, PLATFORM_TO_ROWS, PLATFORM_TO_NODES)).containsExactly(
                    "items[0].platform APP는 이번 입력의 플랫폼 [WEB]에 없습니다.",
                    "items[1].figmaNodeIds의 '9:9'는 WEB 레이어 목록에 없습니다.",
                    "items[2].specRowIds의 'C4'는 WEB C행이 아닙니다.",
                    "items[2]: PASS 판정에는 근거 레이어 ID가 1개 이상 있어야 합니다."
            );
        }
    }

    private static DesignReviewItemOutputDto 항목(
            DesignPlatform platform,
            List<String> rowIds,
            List<String> nodeIds,
            Verdict verdict
    ) {
        return new DesignReviewItemOutputDto(platform, "근거", rowIds, "로그인 버튼 영역 시안이 명세와 같다", "순서와 문구를 확인한다", nodeIds, "사유", verdict);
    }
}
