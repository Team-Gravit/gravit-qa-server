package gravit.qa.checklist.controller.docs;

import gravit.qa.checklist.dto.request.ChecklistItemCreateRequest;
import gravit.qa.checklist.dto.response.ChecklistItemResponse;
import gravit.qa.checklist.dto.response.ChecklistResponse;
import gravit.qa.global.exception.domain.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Checklist API", description = "1차(AUTO), 2차(MANUAL) 체크리스트 조회, 항목 추가")
public interface ChecklistControllerDocs {

    @Operation(
            summary = "체크리스트 조회",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>round=1이면 AUTO(1차), round=2면 MANUAL(2차) 항목을 화면별로 묶어 내려준다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 체크리스트 조회 성공"),
            @ApiResponse(responseCode = "400", description = "🚨 round가 없거나 1, 2가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_4001\", \"message\": \"잘못된 요청 파라미터입니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "🚨 스프린트 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4041\", \"message\": \"존재하지 않는 스프린트입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<ChecklistResponse> getChecklist(
            @Parameter(description = "스프린트 ID") long sprintId,
            @Parameter(description = "1(1차, AUTO) 또는 2(2차, MANUAL)") Integer round
    );

    @Operation(
            summary = "체크 항목 추가",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>체크리스트 생성이 끝난 뒤(DESIGN_REVIEW, AUTO_REVIEW, MANUAL_REVIEW), 실행 중인 작업이 없을 때 가능. 셀은 parts에 있으면 UNCHECKED, 없으면 NOT_APPLICABLE로 만든다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "✅ 체크 항목 추가 성공"),
            @ApiResponse(responseCode = "400", description = "🚨 요청 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_4001\", \"message\": \"잘못된 요청 파라미터입니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "🚨 스프린트 없음, 스프린트 대상 기능이 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SPRINT_4041", value = "{\"error\": \"SPRINT_4041\", \"message\": \"존재하지 않는 스프린트입니다.\"}"),
                                    @ExampleObject(name = "CHECKLIST_4042", value = "{\"error\": \"CHECKLIST_4042\", \"message\": \"스프린트 대상 기능이 아닙니다.\"}")
                            })),
            @ApiResponse(responseCode = "409", description = "🚨 지금 상태에서 편집 불가",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4092\", \"message\": \"현재 스프린트 상태에서는 할 수 없는 작업입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<ChecklistItemResponse> createChecklistItem(
            @Parameter(description = "스프린트 ID") long sprintId,
            ChecklistItemCreateRequest request
    );
}
