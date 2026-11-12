package gravit.qa.fixReport.support;

import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.CodeEvidenceDto;
import gravit.qa.checklist.dto.internal.FailRecordDto;
import gravit.qa.checklist.dto.internal.FailedCellDto;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.designReview.dto.internal.FailedDesignItemDto;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecStatus;
import gravit.qa.feature.dto.internal.FeatureDto;
import gravit.qa.fixReport.dto.internal.FixReportDto;
import gravit.qa.fixReport.dto.internal.FixReportEntryDto;
import gravit.qa.fixReport.dto.internal.FixReportScreenDto;
import gravit.qa.sprint.domain.ProgressPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class FixReportAssemblerTest {

    private static final FeatureDto FIRST = 기능(1L, "A-01");
    private static final FeatureDto SECOND = 기능(2L, "A-02");
    private static final Map<Long, FeatureDto> FEATURES = Map.of(1L, FIRST, 2L, SECOND);

    @Nested
    @DisplayName("웹·앱·서버 파일 내용을 모을 때")
    class 코드 {

        @Test
        void 화면_ID_순서로_묶고_화면_안에서는_1차_2차_순서와_정렬_순서를_따른다() {
            // given
            List<FailedCellDto> cells = List.of(
                    셀(10L, 2L, Classification.MANUAL, 0, "둘째 화면 2차"),
                    셀(11L, 1L, Classification.MANUAL, 0, "첫 화면 2차"),
                    셀(12L, 1L, Classification.AUTO, 5, "첫 화면 1차 뒤"),
                    셀(13L, 1L, Classification.AUTO, 1, "첫 화면 1차 앞")
            );

            // when
            FixReportDto report = FixReportAssembler.assembleCode("Test Agile", ProgressPart.SERVER, "gravit-server", "qa-baseline@aaaaaaa", cells, FEATURES);

            // then
            assertThat(report.screens()).extracting(FixReportScreenDto::screenId).containsExactly("A-01", "A-02");
            assertThat(report.screens().getFirst().entries()).extracting(FixReportEntryDto::title)
                    .containsExactly("첫 화면 1차 앞", "첫 화면 1차 뒤", "첫 화면 2차");
        }

        @Test
        void 사유와_근거를_따로_골라_담는다() {
            // given
            CodeEvidenceDto evidence = CodeEvidenceDto.of("gravit-server/src/A.java", 1, 9);
            FailedCellDto cell = new FailedCellDto(1L, 1L, Classification.AUTO, 0, "행위", "설명", List.of("A-01-B1"), "사람 사유", List.of(), List.of(
                    FailRecordDto.of("사람 사유", List.of()),
                    FailRecordDto.of("1차 사유", List.of(evidence))
            ));

            // when
            FixReportEntryDto entry = FixReportAssembler.assembleCode("Test Agile", ProgressPart.SERVER, "gravit-server", null, List.of(cell), FEATURES)
                    .screens().getFirst().entries().getFirst();

            // then
            assertSoftly(softly -> {
                softly.assertThat(entry.reason()).isEqualTo("사람 사유");
                softly.assertThat(entry.evidence()).containsExactly(evidence);
                softly.assertThat(entry.specRowIds()).containsExactly("A-01-B1");
            });
        }
    }

    @Nested
    @DisplayName("디자인 파일 내용을 모을 때")
    class 디자인 {

        @Test
        void 같은_화면은_웹_앱_순서로_나눠_묶는다() {
            // given
            List<FailedDesignItemDto> items = List.of(
                    디자인(20L, 2L, DesignPlatform.WEB, 0),
                    디자인(21L, 1L, DesignPlatform.APP, 0),
                    디자인(22L, 1L, DesignPlatform.WEB, 3),
                    디자인(23L, 1L, DesignPlatform.WEB, 1)
            );

            // when
            FixReportDto report = FixReportAssembler.assembleDesign("3rd Agile", "AbC123", items, FEATURES);

            // then
            assertThat(report.screens()).extracting(FixReportScreenDto::screenId, FixReportScreenDto::platform).containsExactly(
                    tuple("A-01", DesignPlatform.WEB),
                    tuple("A-01", DesignPlatform.APP),
                    tuple("A-02", DesignPlatform.WEB)
            );
            assertThat(report.screens().getFirst().entries()).extracting(FixReportEntryDto::title)
                    .containsExactly("디자인 23", "디자인 22");
            assertThat(report.screens().getFirst().entries().getFirst().figmaNodeIds()).containsExactly("1:23");
        }
    }

    private static FeatureDto 기능(
            long id,
            String screenId
    ) {
        return new FeatureDto(id, screenId, screenId + " 화면", "/route", List.of(Part.SERVER, Part.WEB, Part.APP), SpecStatus.CONFIRMED, List.of(), "page-" + id);
    }

    private static FailedCellDto 셀(
            long itemId,
            long featureId,
            Classification classification,
            int sortOrder,
            String title
    ) {
        return new FailedCellDto(itemId, featureId, classification, sortOrder, title, "설명", List.of(), "사유", List.of(), List.of());
    }

    private static FailedDesignItemDto 디자인(
            long itemId,
            long featureId,
            DesignPlatform platform,
            int sortOrder
    ) {
        return new FailedDesignItemDto(itemId, featureId, platform, sortOrder, "디자인 " + itemId, "설명", List.of("C1"), List.of("1:" + itemId), "사유", List.of());
    }
}
