package gravit.qa.checklist.controller.docs;

import gravit.qa.checklist.dto.request.ChecklistConfirmRequest;
import gravit.qa.checklist.dto.request.ChecklistItemCreateRequest;
import gravit.qa.checklist.dto.response.ChecklistConfirmResponse;
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

@Tag(name = "Checklist API", description = "체크리스트 조회, 항목 추가, 분류 확정")
public interface ChecklistControllerDocs {

    @Operation(
            summary = "체크리스트 조회",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>기능별 체크 항목 × 파트 셀. 셀마다 수동 변경 가능 여부(editable)를 내려준다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 체크리스트 조회 성공"),
            @ApiResponse(responseCode = "404", description = "🚨 스프린트 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4041\", \"message\": \"존재하지 않는 스프린트입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<ChecklistResponse> getChecklist(@Parameter(description = "스프린트 ID") long sprintId);

    @Operation(
            summary = "체크 항목 수동 추가",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>REVIEW 전용. origin = HUMAN, reviewState = CONFIRMED로 만든다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "✅ 체크 항목 추가 성공"),
            @ApiResponse(responseCode = "400", description = "🚨 요청 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_4001\", \"message\": \"잘못된 요청 파라미터입니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "🚨 스프린트 없음, 스프린트 대상 기능 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SPRINT_4041", value = "{\"error\": \"SPRINT_4041\", \"message\": \"존재하지 않는 스프린트입니다.\"}"),
                                    @ExampleObject(name = "CHECKLIST_4042", value = "{\"error\": \"CHECKLIST_4042\", \"message\": \"스프린트 대상 기능이 아닙니다.\"}")
                            })),
            @ApiResponse(responseCode = "409", description = "🚨 REVIEW 상태가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4092\", \"message\": \"현재 스프린트 상태에서는 할 수 없는 작업입니다.\"}"))),
            @ApiResponse(responseCode = "422", description = "🚨 카탈로그에 없는 testRef, 모르는 personaKey",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "CHECKLIST_4221", value = "{\"error\": \"CHECKLIST_4221\", \"message\": \"테스트 카탈로그에 없는 testRef입니다. 자동 항목에는 카탈로그의 테스트가 있어야 합니다.\"}"),
                                    @ExampleObject(name = "CHECKLIST_4222", value = "{\"error\": \"CHECKLIST_4222\", \"message\": \"알 수 없는 페르소나 키입니다.\"}")
                            })),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<ChecklistItemResponse> createChecklistItem(
            @Parameter(description = "스프린트 ID") long sprintId,
            ChecklistItemCreateRequest request
    );

    @Operation(
            summary = "분류 확정 (REVIEW → IN_PROGRESS)",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>모든 항목이 CONFIRMED 또는 REJECTED여야 한다. AUTO 항목이 있고 startAutoRun이 true면 자동 실행을 큐에 넣는다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 분류 확정 성공"),
            @ApiResponse(responseCode = "404", description = "🚨 스프린트 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4041\", \"message\": \"존재하지 않는 스프린트입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "🚨 상태 불가, 검토 남은 항목 있음, version 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SPRINT_4092", value = "{\"error\": \"SPRINT_4092\", \"message\": \"현재 스프린트 상태에서는 할 수 없는 작업입니다.\"}"),
                                    @ExampleObject(name = "CHECKLIST_4091", value = "{\"error\": \"CHECKLIST_4091\", \"message\": \"검토하지 않은 체크 항목이 남아 있습니다.\"}"),
                                    @ExampleObject(name = "GLOBAL_4091", value = "{\"error\": \"GLOBAL_4091\", \"message\": \"다른 사용자가 먼저 변경했습니다. 다시 불러온 뒤 시도하세요.\"}")
                            })),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<ChecklistConfirmResponse> confirmChecklist(
            @Parameter(description = "스프린트 ID") long sprintId,
            ChecklistConfirmRequest request
    );
}
