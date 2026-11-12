package gravit.qa.fixReport.controller;

import gravit.qa.checklist.domain.CellState;
import gravit.qa.checklist.domain.ChecklistItem;
import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.CodeEvidenceDto;
import gravit.qa.checklist.repository.CheckCellRepository;
import gravit.qa.designReview.domain.DesignPlatform;
import gravit.qa.designReview.domain.DesignReviewItem;
import gravit.qa.feature.domain.Feature;
import gravit.qa.feature.domain.Part;
import gravit.qa.job.domain.JobType;
import gravit.qa.job.service.JobCommandService;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.fixture.SprintFixture;
import gravit.qa.support.QaSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@QaSpringBootTest
@AutoConfigureMockMvc
@Import(SprintFixture.class)
class FixReportControllerIntegrationTest {

    private static final String BEARER = "Bearer admin-access-token";
    private static final String SPRINT_KEY = "Test Agile";
    private static final String SPRINT_PAGE_ID = "22222222222222222222222222222222";
    private static final CodeEvidenceDto DETAIL_EVIDENCE = CodeEvidenceDto.of("gravit-server/src/main/java/gravit/code/notice/dto/response/NoticeDetailResponse.java", 8, 20);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SprintFixture sprintFixture;

    @Autowired
    private JobCommandService jobCommandService;

    @Autowired
    private CheckCellRepository checkCellRepository;

    @Nested
    @DisplayName("현황을 조회할 때")
    class 현황 {

        @Test
        void 미검수가_없는_MANUAL_REVIEW면_ready이고_파트별_FAIL_수와_파일_이름을_순서대로_내려준다() throws Exception {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = sprintFixture.기능("NOTC-02", List.of(Part.SERVER, Part.WEB, Part.APP));
            ChecklistItem auto = 항목(sprint, feature, Classification.AUTO, List.of(Part.SERVER, Part.WEB), "1차 항목", 0);
            sprintFixture.셀_판정(auto.getId(), Part.SERVER, CellState.FAIL, "필드가 없다");
            sprintFixture.셀_판정(auto.getId(), Part.WEB, CellState.PASS, "확인");
            ChecklistItem manual = 항목(sprint, feature, Classification.MANUAL, List.of(Part.WEB), "2차 항목", 1);
            sprintFixture.셀_체크(manual.getId(), Part.WEB, CellState.FAIL);
            sprintFixture.디자인_항목(sprint.getId(), feature.getId(), DesignPlatform.WEB, CellState.FAIL);
            sprintFixture.디자인_항목(sprint.getId(), feature.getId(), DesignPlatform.APP, CellState.PASS);

            // when & then
            현황(sprint.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sprintId").value(sprint.getId()))
                    .andExpect(jsonPath("$.status").value("MANUAL_REVIEW"))
                    .andExpect(jsonPath("$.ready").value(true))
                    .andExpect(jsonPath("$.unchecked.design").value(0))
                    .andExpect(jsonPath("$.unchecked.first").value(0))
                    .andExpect(jsonPath("$.unchecked.second").value(0))
                    .andExpect(jsonPath("$.parts", hasSize(4)))
                    .andExpect(jsonPath("$.parts[0].part").value("DESIGN"))
                    .andExpect(jsonPath("$.parts[0].failCount").value(1))
                    .andExpect(jsonPath("$.parts[0].fileName").value("test-agile_DESIGN_fixes.txt"))
                    .andExpect(jsonPath("$.parts[1].part").value("WEB"))
                    .andExpect(jsonPath("$.parts[1].failCount").value(1))
                    .andExpect(jsonPath("$.parts[1].fileName").value("test-agile_WEB_fixes.txt"))
                    .andExpect(jsonPath("$.parts[2].part").value("APP"))
                    .andExpect(jsonPath("$.parts[2].failCount").value(0))
                    .andExpect(jsonPath("$.parts[2].fileName").value("test-agile_APP_fixes.txt"))
                    .andExpect(jsonPath("$.parts[3].part").value("SERVER"))
                    .andExpect(jsonPath("$.parts[3].failCount").value(1))
                    .andExpect(jsonPath("$.parts[3].fileName").value("test-agile_SERVER_fixes.txt"));
        }

        @Test
        void 미검수는_디자인_1차_2차로_나눠_세고_남아_있으면_ready가_아니다() throws Exception {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = sprintFixture.기능("NOTC-02", List.of(Part.SERVER, Part.WEB, Part.APP));
            ChecklistItem auto = 항목(sprint, feature, Classification.AUTO, List.of(Part.SERVER, Part.WEB), "1차 항목", 0);
            sprintFixture.셀_판정(auto.getId(), Part.WEB, CellState.FAIL, "문구가 다르다");
            항목(sprint, feature, Classification.MANUAL, List.of(Part.WEB, Part.APP), "2차 항목", 1);
            sprintFixture.디자인_항목(sprint.getId(), feature.getId(), DesignPlatform.WEB, CellState.UNCHECKED);

            // when & then
            현황(sprint.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ready").value(false))
                    .andExpect(jsonPath("$.unchecked.design").value(1))
                    .andExpect(jsonPath("$.unchecked.first").value(1))
                    .andExpect(jsonPath("$.unchecked.second").value(2))
                    .andExpect(jsonPath("$.parts[1].failCount").value(1));
        }

        @Test
        void 실행_중인_작업이_있으면_ready가_아니다() throws Exception {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);
            jobCommandService.enqueue(JobType.QA_PREPARE, sprint.getId(), Map.of());

            // when & then
            현황(sprint.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ready").value(false));
        }

        @Test
        void MANUAL_REVIEW_전이면_미검수가_없어도_ready가_아니고_CLOSED면_ready다() throws Exception {
            // given
            Sprint autoReview = 스프린트(SprintStatus.AUTO_REVIEW);
            Sprint closed = sprintFixture.스프린트("Closed Agile", "33333333333333333333333333333333", SprintStatus.CLOSED);
            Feature feature = sprintFixture.기능("NOTC-02", List.of(Part.WEB));
            항목(closed, feature, Classification.MANUAL, List.of(Part.WEB), "남은 항목", 0);

            // when & then
            현황(autoReview.getId())
                    .andExpect(jsonPath("$.ready").value(false));
            현황(closed.getId())
                    .andExpect(jsonPath("$.ready").value(true))
                    .andExpect(jsonPath("$.unchecked.second").value(1));
        }

        @Test
        void 없는_스프린트면_404_SPRINT_4041() throws Exception {
            // when & then
            현황(999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SPRINT_4041"));
        }
    }

    @Nested
    @DisplayName("파트 파일을 내려받을 때")
    class 내려받기 {

        @Test
        void 서버_파일은_화면_순서와_1차_2차_순서로_FAIL_셀을_담고_첨부_파일_헤더를_붙인다() throws Exception {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);
            Feature list = sprintFixture.기능("NOTC-01", List.of(Part.SERVER));
            Feature detail = sprintFixture.기능("NOTC-02", List.of(Part.SERVER, Part.WEB));

            ChecklistItem move = 항목(sprint, detail, Classification.AUTO, List.of(Part.SERVER, Part.WEB), "이전·다음 공지로 이동한다", "상세 응답에 이전·다음 공지 정보가 있다", List.of("NOTC-02-B2"), 0);
            sprintFixture.셀_판정_기록(move.getId(), Part.SERVER, CellState.FAIL, "응답 DTO에 이전·다음 공지 필드가 없다", List.of(DETAIL_EVIDENCE));
            sprintFixture.셀_판정_기록(move.getId(), Part.WEB, CellState.FAIL, "이전·다음 링크가 없다", List.of());
            ChecklistItem order = 항목(sprint, list, Classification.MANUAL, List.of(Part.SERVER), "공지 목록을 최신순으로 보여준다", "작성일 내림차순이다", List.of("NOTC-01-B2"), 0);
            sprintFixture.셀_체크_기록(order.getId(), Part.SERVER, CellState.FAIL, "정렬 조건이 없다");
            ChecklistItem pinned = 항목(sprint, list, Classification.AUTO, List.of(Part.SERVER), "고정 공지를 맨 위에 둔다", "고정 공지가 먼저 나온다", List.of("NOTC-01-B1"), 1);
            sprintFixture.셀_판정_기록(pinned.getId(), Part.SERVER, CellState.FAIL, "고정 여부로 정렬하지 않는다", List.of());
            ChecklistItem passed = 항목(sprint, list, Classification.AUTO, List.of(Part.SERVER), "통과한 항목", "설명", List.of("NOTC-01-B3"), 2);
            sprintFixture.셀_판정_기록(passed.getId(), Part.SERVER, CellState.PASS, "확인", List.of());

            // when
            MvcResult result = 내려받기(sprint.getId(), "SERVER")
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"test-agile_SERVER_fixes.txt\"; filename*=UTF-8''test-agile_SERVER_fixes.txt"))
                    .andReturn();

            // then
            MediaType contentType = MediaType.parseMediaType(result.getResponse().getContentType());
            assertThat(contentType.isCompatibleWith(MediaType.TEXT_PLAIN)).isTrue();
            assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.UTF_8);
            assertThat(본문(result)).isEqualTo("""
                    Test Agile / SERVER 수정사항 3건
                    기준 코드: gravit-server qa-baseline@aaaaaaa
                    기대 결과 우선순위: Notion 명세 > Figma 시안 > 코드

                    [NOTC-01] NOTC-01 화면
                    명세: https://www.notion.so/%s

                    1. 고정 공지를 맨 위에 둔다
                       기대: 고정 공지가 먼저 나온다
                       문제: 고정 여부로 정렬하지 않는다
                       명세 행: NOTC-01-B1

                    2. 공지 목록을 최신순으로 보여준다
                       기대: 작성일 내림차순이다
                       문제: 정렬 조건이 없다
                       명세 행: NOTC-01-B2

                    [NOTC-02] NOTC-02 화면
                    명세: https://www.notion.so/%s

                    3. 이전·다음 공지로 이동한다
                       기대: 상세 응답에 이전·다음 공지 정보가 있다
                       문제: 응답 DTO에 이전·다음 공지 필드가 없다
                       명세 행: NOTC-02-B2
                       코드: gravit-server/src/main/java/gravit/code/notice/dto/response/NoticeDetailResponse.java:8-20
                    """.formatted(list.getNotionPageId(), detail.getNotionPageId()));
        }

        @Test
        void 여러_파트에서_FAIL인_항목은_파트마다_그_파일에_들어간다() throws Exception {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);
            Feature detail = sprintFixture.기능("NOTC-02", List.of(Part.SERVER, Part.WEB));
            ChecklistItem move = 항목(sprint, detail, Classification.AUTO, List.of(Part.SERVER, Part.WEB), "이전·다음 공지로 이동한다", "설명", List.of("NOTC-02-B2"), 0);
            sprintFixture.셀_판정_기록(move.getId(), Part.SERVER, CellState.FAIL, "응답 필드가 없다", List.of());
            sprintFixture.셀_판정_기록(move.getId(), Part.WEB, CellState.FAIL, "이전·다음 링크가 없다", List.of());

            // when
            String server = 본문(내려받기(sprint.getId(), "SERVER").andExpect(status().isOk()).andReturn());
            String web = 본문(내려받기(sprint.getId(), "WEB").andExpect(status().isOk()).andReturn());

            // then
            assertThat(server).contains("1. 이전·다음 공지로 이동한다", "문제: 응답 필드가 없다").doesNotContain("이전·다음 링크가 없다");
            assertThat(web).startsWith("Test Agile / WEB 수정사항 1건\n기준 코드: gravit-web qa-baseline@bbbbbbb\n")
                    .contains("1. 이전·다음 공지로 이동한다", "문제: 이전·다음 링크가 없다");
        }

        @Test
        void 디자인_파일은_화면마다_웹_앱_순서로_묶고_Figma_노드마다_시안_줄을_쓴다() throws Exception {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);
            Feature login = sprintFixture.기능("LGN-01", List.of(Part.WEB, Part.APP));
            sprintFixture.디자인_항목(sprint.getId(), login.getId(), DesignPlatform.APP, CellState.FAIL, "앱 버튼 순서", null, List.of(), 0);
            sprintFixture.디자인_항목(sprint.getId(), login.getId(), DesignPlatform.WEB, CellState.FAIL, "웹 버튼 순서", "애플 버튼이 맨 앞이다", List.of("12:34", "12:56"), 0);
            sprintFixture.디자인_항목(sprint.getId(), login.getId(), DesignPlatform.WEB, CellState.PASS, "통과한 시안", "같다", List.of("9:9"), 1);

            // when
            MvcResult result = 내려받기(sprint.getId(), "DESIGN")
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"test-agile_DESIGN_fixes.txt\"; filename*=UTF-8''test-agile_DESIGN_fixes.txt"))
                    .andReturn();

            // then
            assertThat(본문(result)).isEqualTo("""
                    Test Agile / DESIGN 수정사항 2건
                    기준 시안: Figma AbC123
                    기대 결과 우선순위: Notion 명세 > Figma 시안 > 코드

                    [LGN-01] LGN-01 화면 · 웹
                    명세: https://www.notion.so/%1$s

                    1. 웹 버튼 순서
                       기대: 웹 버튼 순서 설명
                       문제: 애플 버튼이 맨 앞이다
                       명세 행: C1
                       시안: https://www.figma.com/design/AbC123?node-id=12-34
                       시안: https://www.figma.com/design/AbC123?node-id=12-56

                    [LGN-01] LGN-01 화면 · 앱
                    명세: https://www.notion.so/%1$s

                    2. 앱 버튼 순서
                       기대: 앱 버튼 순서 설명
                       문제: (사유 없음)
                       명세 행: C1
                    """.formatted(login.getNotionPageId()));
        }

        @Test
        void 사람이_사유_없이_다시_FAIL로_바꾸면_1차_사유와_근거를_쓰고_항목을_고친_뒤면_고치기_전_기록은_쓰지_않는다() throws Exception {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = sprintFixture.기능("NOTC-02", List.of(Part.WEB));
            CodeEvidenceDto evidence = CodeEvidenceDto.of("gravit-web/apps/web/src/pages/notice-detail/ui/notice-detail-page.tsx", 73, 84);

            ChecklistItem toggled = 항목(sprint, feature, Classification.AUTO, List.of(Part.WEB), "다시 FAIL로 바꾼 항목", "설명", List.of("NOTC-02-B1"), 0);
            sprintFixture.셀_판정_기록(toggled.getId(), Part.WEB, CellState.FAIL, "1차 사유", List.of(evidence));
            셀_변경(toggled.getId(), "PASS", null).andExpect(status().isOk());
            셀_변경(toggled.getId(), "FAIL", null).andExpect(status().isOk());

            ChecklistItem edited = 항목(sprint, feature, Classification.AUTO, List.of(Part.WEB), "고치기 전 행위", "설명", List.of("NOTC-02-B2"), 1);
            sprintFixture.셀_판정_기록(edited.getId(), Part.WEB, CellState.FAIL, "고치기 전 사유", List.of(evidence));
            mockMvc.perform(patch("/api/v1/checklist-items/" + edited.getId())
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\": \"고친 행위\", \"version\": 0}"))
                    .andExpect(status().isOk());
            셀_변경(edited.getId(), "FAIL", null).andExpect(status().isOk());

            // when
            String web = 본문(내려받기(sprint.getId(), "WEB").andExpect(status().isOk()).andReturn());

            // then
            assertThat(web).contains("""
                    1. 다시 FAIL로 바꾼 항목
                       기대: 설명
                       문제: 1차 사유
                       명세 행: NOTC-02-B1
                       코드: gravit-web/apps/web/src/pages/notice-detail/ui/notice-detail-page.tsx:73-84

                    2. 고친 행위
                       기대: 설명
                       문제: (사유 없음)
                       명세 행: NOTC-02-B2
                    """);
            assertThat(web).doesNotContain("고치기 전 사유");
        }

        @Test
        void 사람이_UNCHECKED로_둔_뒤_항목을_고치고_사유_없이_FAIL로_바꾸면_수정_전_1차_사유와_근거를_쓰지_않는다() throws Exception {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = sprintFixture.기능("NOTC-02", List.of(Part.WEB));
            CodeEvidenceDto evidence = CodeEvidenceDto.of("gravit-web/apps/web/src/pages/notice-detail/ui/notice-detail-page.tsx", 73, 84);
            ChecklistItem item = 항목(sprint, feature, Classification.AUTO, List.of(Part.WEB), "고치기 전 행위", "설명", List.of("NOTC-02-B1"), 0);
            sprintFixture.셀_판정_기록(item.getId(), Part.WEB, CellState.FAIL, "수정 전 1차 사유", List.of(evidence));
            셀_변경(item.getId(), "UNCHECKED", null).andExpect(status().isOk());
            mockMvc.perform(patch("/api/v1/checklist-items/" + item.getId())
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\": \"고친 행위\", \"version\": 0}"))
                    .andExpect(status().isOk());
            셀_변경(item.getId(), "FAIL", null).andExpect(status().isOk());

            // when
            String web = 본문(내려받기(sprint.getId(), "WEB").andExpect(status().isOk()).andReturn());

            // then
            assertThat(web).contains("""
                    1. 고친 행위
                       기대: 설명
                       문제: (사유 없음)
                       명세 행: NOTC-02-B1
                    """);
            assertThat(web).doesNotContain("수정 전 1차 사유", "   코드: ");
        }

        @Test
        void 사람이_미검수로_둔_디자인_항목을_고치고_사유_없이_FAIL로_바꾸면_수정_전_자동_비교_사유를_쓰지_않는다() throws Exception {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);
            Feature login = sprintFixture.기능("LGN-01", List.of(Part.WEB));
            DesignReviewItem item = sprintFixture.디자인_항목(sprint.getId(), login.getId(), DesignPlatform.WEB, CellState.FAIL, "고치기 전 행위", "수정 전 자동 비교 사유", List.of(), 0);
            디자인_상태_변경(item.getId(), "UNCHECKED", 0).andExpect(status().isOk());
            mockMvc.perform(patch("/api/v1/design-review-items/" + item.getId())
                            .header("Authorization", BEARER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\": \"고친 행위\", \"version\": 1}"))
                    .andExpect(status().isOk());
            디자인_상태_변경(item.getId(), "FAIL", 2).andExpect(status().isOk());

            // when
            String design = 본문(내려받기(sprint.getId(), "DESIGN").andExpect(status().isOk()).andReturn());

            // then
            assertThat(design).contains("""
                    1. 고친 행위
                       기대: 고치기 전 행위 설명
                       문제: (사유 없음)
                    """);
            assertThat(design).doesNotContain("수정 전 자동 비교 사유");
        }

        @Test
        void Fail이_없으면_제목_줄과_기준_줄_뒤에_수정할_항목이_없다고_쓴다() throws Exception {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);

            // when
            String app = 본문(내려받기(sprint.getId(), "APP").andExpect(status().isOk()).andReturn());

            // then
            assertThat(app).isEqualTo("""
                    Test Agile / APP 수정사항 0건
                    기준 코드: gravit-aos qa-baseline@ccccccc

                    수정할 항목이 없습니다.
                    """);
        }

        @Test
        void CLOSED면_내려받을_수_있다() throws Exception {
            // given
            Sprint sprint = sprintFixture.스프린트(SPRINT_KEY, SPRINT_PAGE_ID, SprintStatus.CLOSED);

            // when & then
            내려받기(sprint.getId(), "SERVER")
                    .andExpect(status().isOk());
        }

        @Test
        void ready가_아니면_409_FIX_REPORT_4091() throws Exception {
            // given
            Sprint unchecked = 스프린트(SprintStatus.MANUAL_REVIEW);
            Feature feature = sprintFixture.기능("NOTC-02", List.of(Part.WEB));
            항목(unchecked, feature, Classification.MANUAL, List.of(Part.WEB), "남은 항목", 0);
            Sprint preparing = sprintFixture.스프린트("Preparing Agile", "44444444444444444444444444444444", SprintStatus.AUTO_REVIEW);

            // when & then
            내려받기(unchecked.getId(), "WEB")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("FIX_REPORT_4091"));
            내려받기(preparing.getId(), "DESIGN")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("FIX_REPORT_4091"));
        }

        @Test
        void 실행_중인_작업이_있으면_409_FIX_REPORT_4091() throws Exception {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);
            jobCommandService.enqueue(JobType.QA_PREPARE, sprint.getId(), Map.of());

            // when & then
            내려받기(sprint.getId(), "SERVER")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("FIX_REPORT_4091"));
        }

        @Test
        void 파트가_DESIGN_WEB_APP_SERVER가_아니면_400_GLOBAL_4001() throws Exception {
            // given
            Sprint sprint = 스프린트(SprintStatus.MANUAL_REVIEW);

            // when & then
            내려받기(sprint.getId(), "server")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4001"));
            내려받기(sprint.getId(), "DESIGNER")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("GLOBAL_4001"));
        }

        @Test
        void 없는_스프린트면_404_SPRINT_4041() throws Exception {
            // when & then
            내려받기(999L, "SERVER")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SPRINT_4041"));
        }
    }

    private Sprint 스프린트(SprintStatus status) {
        Sprint sprint = sprintFixture.스프린트(SPRINT_KEY, SPRINT_PAGE_ID, status);
        sprintFixture.연결_성공(sprint.getId());
        return sprint;
    }

    private ChecklistItem 항목(
            Sprint sprint,
            Feature feature,
            Classification classification,
            List<Part> parts,
            String title,
            int sortOrder
    ) {
        return 항목(sprint, feature, classification, parts, title, "설명", List.of("C1"), sortOrder);
    }

    private ChecklistItem 항목(
            Sprint sprint,
            Feature feature,
            Classification classification,
            List<Part> parts,
            String title,
            String expected,
            List<String> specRowIds,
            int sortOrder
    ) {
        return sprintFixture.항목(sprint.getId(), feature.getId(), classification, parts, title, expected, specRowIds, sortOrder);
    }

    private ResultActions 현황(long sprintId) throws Exception {
        return mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/fix-reports").header("Authorization", BEARER));
    }

    private ResultActions 내려받기(
            long sprintId,
            String part
    ) throws Exception {
        return mockMvc.perform(get("/api/v1/sprints/" + sprintId + "/fix-reports/" + part).header("Authorization", BEARER));
    }

    private ResultActions 셀_변경(
            long itemId,
            String state,
            String reason
    ) throws Exception {
        int version = checkCellRepository.findByItemIdAndPart(itemId, Part.WEB).orElseThrow().getVersion();
        String reasonField = reason == null ? "" : ", \"reason\": \"" + reason + "\"";
        return mockMvc.perform(put("/api/v1/checklist-items/" + itemId + "/cells/WEB")
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\": \"" + state + "\", \"version\": " + version + reasonField + "}"));
    }

    private ResultActions 디자인_상태_변경(
            long itemId,
            String state,
            int version
    ) throws Exception {
        return mockMvc.perform(put("/api/v1/design-review-items/" + itemId)
                .header("Authorization", BEARER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\": \"" + state + "\", \"version\": " + version + "}"));
    }

    private String 본문(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
