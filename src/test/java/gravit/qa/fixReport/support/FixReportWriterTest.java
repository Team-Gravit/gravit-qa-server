package gravit.qa.fixReport.support;

import gravit.qa.checklist.dto.internal.CodeEvidenceDto;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.fixReport.dto.internal.FixReportDto;
import gravit.qa.fixReport.dto.internal.FixReportEntryDto;
import gravit.qa.fixReport.dto.internal.FixReportScreenDto;
import gravit.qa.sprint.domain.ProgressPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixReportWriterTest {

    private static final String REF = "qa-baseline@0eced9c";

    @Nested
    @DisplayName("웹·앱·서버 파일을 쓸 때")
    class 코드_파일 {

        @Test
        void 고정_입력이면_골든_파일과_같다() throws IOException {
            // given
            FixReportScreenDto list = FixReportScreenDto.of(1L, "NOTC-01", "공지사항 목록", "4145b32f-003c-81b5-876e-def41689dfc3", null, List.of(
                    FixReportEntryDto.of(
                            "공지 목록을 최신순으로 보여준다",
                            "목록 응답이 작성일 내림차순이다\r\n\r\n  - 고정 공지는 맨 위다  ",
                            "정렬 조건이 없다\n",
                            List.of("NOTC-01-B1", "NOTC-01-B2"),
                            List.of(
                                    CodeEvidenceDto.of("gravit-server/src/main/java/gravit/code/notice/service/NoticeQueryService.java", 30, 42),
                                    CodeEvidenceDto.of("gravit-server/src/main/java/gravit/code/notice/repository/NoticeRepository.java", 12, 15)
                            ),
                            List.of()
                    ),
                    FixReportEntryDto.of("빈 목록 안내", "공지가 없으면 안내 문구를 보여준다", null, List.of(), List.of(), List.of())
            ));
            FixReportScreenDto detail = FixReportScreenDto.of(2L, "NOTC-02", "공지사항 상세", "5255c43f114d92c6987fef052790ead4", null, List.of(
                    FixReportEntryDto.of(
                            "이전·다음 공지로 이동한다",
                            "상세 응답에 이전·다음 공지 정보가 있다",
                            "응답 DTO에 이전·다음 공지 필드가 없다",
                            List.of("NOTC-02-B2"),
                            List.of(CodeEvidenceDto.of("gravit-server/src/main/java/gravit/code/notice/dto/response/NoticeDetailResponse.java", 8, 20)),
                            List.of()
                    )
            ));

            // when
            String text = FixReportWriter.write(FixReportDto.code("Test Agile", ProgressPart.SERVER, "gravit-server", REF, List.of(list, detail)));

            // then
            assertThat(text).isEqualTo(golden("server_fixes.txt"));
        }

        @Test
        void 기준_커밋이_없으면_기준_코드_줄을_뺀다() {
            // given
            FixReportScreenDto screen = FixReportScreenDto.of(1L, "LRN-02", "학습", "abc", null, List.of(
                    FixReportEntryDto.of("행위", "설명", "사유", List.of(), List.of(), List.of())
            ));

            // when
            String text = FixReportWriter.write(FixReportDto.code("Test Agile", ProgressPart.WEB, "gravit-web", null, List.of(screen)));

            // then
            assertThat(text).startsWith("""
                    Test Agile / WEB 수정사항 1건
                    기대 결과 우선순위: Notion 명세 > Figma 시안 > 코드

                    [LRN-02] 학습
                    """);
        }
    }

    @Nested
    @DisplayName("디자인 파일을 쓸 때")
    class 디자인_파일 {

        @Test
        void 고정_입력이면_골든_파일과_같다() throws IOException {
            // given
            FixReportScreenDto web = FixReportScreenDto.of(1L, "LGN-01", "로그인", "6366d54f225ea3d7a98a0f1638a1fbe5", DesignPlatform.WEB, List.of(
                    FixReportEntryDto.of("로그인 버튼 영역\n소셜 버튼 순서", "카카오, 구글, 애플 순서다 (LGN-01-C1)", "애플 버튼이 맨 앞이다", List.of("LGN-01-C1"), List.of(), List.of("12:34", "12:56"))
            ));
            FixReportScreenDto app = FixReportScreenDto.of(1L, "LGN-01", "로그인", "6366d54f225ea3d7a98a0f1638a1fbe5", DesignPlatform.APP, List.of(
                    FixReportEntryDto.of("로그인 버튼 영역", "카카오, 구글 순서다 (LGN-01-C1)", null, List.of("LGN-01-C1"), List.of(), List.of())
            ));

            // when
            String text = FixReportWriter.write(FixReportDto.design("3rd Agile", "AbC123", List.of(web, app)));

            // then
            assertThat(text).isEqualTo(golden("design_fixes.txt"));
        }

        @Test
        void Figma_파일_키가_없으면_기준_시안_줄과_시안_줄을_뺀다() {
            // given
            FixReportScreenDto web = FixReportScreenDto.of(1L, "LGN-01", "로그인", "abc", DesignPlatform.WEB, List.of(
                    FixReportEntryDto.of("행위", "설명", "사유", List.of("LGN-01-C1"), List.of(), List.of("12:34"))
            ));

            // when
            String text = FixReportWriter.write(FixReportDto.design("3rd Agile", null, List.of(web)));

            // then
            assertThat(text).isEqualTo("""
                    3rd Agile / DESIGN 수정사항 1건
                    기대 결과 우선순위: Notion 명세 > Figma 시안 > 코드

                    [LGN-01] 로그인 · 웹
                    명세: https://www.notion.so/abc

                    1. 행위
                       기대: 설명
                       문제: 사유
                       명세 행: LGN-01-C1
                    """);
        }
    }

    @Nested
    @DisplayName("Fail이 없을 때")
    class 빈_파일 {

        @Test
        void 제목_줄과_기준_줄_뒤에_수정할_항목이_없다고_쓴다() throws IOException {
            // when
            String text = FixReportWriter.write(FixReportDto.code("Test Agile", ProgressPart.SERVER, "gravit-server", REF, List.of()));

            // then
            assertThat(text).isEqualTo(golden("empty_fixes.txt"));
        }

        @Test
        void 기준_줄이_없으면_제목_줄만_남긴다() {
            // when
            String text = FixReportWriter.write(FixReportDto.design("3rd\nAgile", null, List.of()));

            // then
            assertThat(text).isEqualTo("""
                    3rd Agile / DESIGN 수정사항 0건

                    수정할 항목이 없습니다.
                    """);
        }
    }

    private String golden(String fileName) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/fixReport/" + fileName)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
