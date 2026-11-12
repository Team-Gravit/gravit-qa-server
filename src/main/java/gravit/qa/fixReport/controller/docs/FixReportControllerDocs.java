package gravit.qa.fixReport.controller.docs;

import gravit.qa.fixReport.dto.response.FixReportResponse;
import gravit.qa.global.exception.domain.ErrorResponse;
import gravit.qa.sprint.domain.ProgressPart;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Fix Report API", description = "파트별 수정사항 파일(TXT) 현황과 내려받기")
public interface FixReportControllerDocs {

    @Operation(
            summary = "수정사항 파일 현황",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>내려받을 수 있는지(ready), 미검수 수(디자인, 1차, 2차), 파트별 FAIL 수와 파일 이름을 내려준다. parts는 항상 DESIGN, WEB, APP, SERVER 순서의 4개다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 수정사항 파일 현황 조회 성공"),
            @ApiResponse(responseCode = "404", description = "🚨 스프린트 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4041\", \"message\": \"존재하지 않는 스프린트입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<FixReportResponse> getFixReports(@Parameter(description = "스프린트 ID") long sprintId);

    @Operation(
            summary = "파트별 수정사항 파일 내려받기",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>그 파트의 FAIL 항목을 정해진 틀에 채운 TXT(UTF-8, LF)를 첨부 파일로 내려준다. LLM을 부르지 않고 DB에 쓰지 않는다. "
                    + "ready가 true일 때만 된다(MANUAL_REVIEW이고 실행 중인 작업이 없고 미검수가 모두 0, 또는 CLOSED). FAIL이 없으면 머리 줄과 \"수정할 항목이 없습니다.\"를 보낸다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 수정사항 파일 내려받기 성공",
                    content = @Content(mediaType = "text/plain",
                            schema = @Schema(type = "string"),
                            examples = @ExampleObject(value = """
                                    Test Agile / SERVER 수정사항 1건
                                    기준 코드: gravit-server qa-baseline@0eced9c
                                    기대 결과 우선순위: Notion 명세 > Figma 시안 > 코드

                                    [NOTC-02] 공지사항 상세
                                    명세: https://www.notion.so/4145b32f003c81b5876edef41689dfc3

                                    1. 이전·다음 공지로 이동한다
                                       기대: 상세 응답에 이전·다음 공지 정보가 있다
                                       문제: 응답 DTO에 이전·다음 공지 필드가 없다
                                       명세 행: NOTC-02-B2
                                       코드: gravit-server/src/main/java/gravit/code/notice/dto/response/NoticeDetailResponse.java:8-20
                                    """))),
            @ApiResponse(responseCode = "400", description = "🚨 part가 DESIGN, WEB, APP, SERVER가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_4001\", \"message\": \"잘못된 요청 파라미터입니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "🚨 스프린트 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4041\", \"message\": \"존재하지 않는 스프린트입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "🚨 미검수 항목이 남았거나 실행 중인 작업이 있음, 또는 MANUAL_REVIEW·CLOSED가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"FIX_REPORT_4091\", \"message\": \"미검수 항목이 남았거나 실행 중인 작업이 있어 수정사항 파일을 만들 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<byte[]> downloadFixReport(
            @Parameter(description = "스프린트 ID") long sprintId,
            @Parameter(description = "DESIGN, WEB, APP, SERVER 중 하나(대소문자 구분)") ProgressPart part
    );
}
