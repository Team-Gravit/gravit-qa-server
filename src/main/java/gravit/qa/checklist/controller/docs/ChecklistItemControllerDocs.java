package gravit.qa.checklist.controller.docs;

import gravit.qa.checklist.dto.request.CheckCellUpdateRequest;
import gravit.qa.checklist.dto.request.ChecklistItemUpdateRequest;
import gravit.qa.checklist.dto.response.CheckCellHistoryResponse;
import gravit.qa.checklist.dto.response.CheckCellResponse;
import gravit.qa.checklist.dto.response.ChecklistItemResponse;
import gravit.qa.feature.domain.Part;
import gravit.qa.global.exception.domain.ErrorResponse;
import gravit.qa.security.principal.LoginAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@Tag(name = "Checklist Item API", description = "체크 항목 수정, 수동 체크, 셀 이력")
public interface ChecklistItemControllerDocs {

    @Operation(
            summary = "체크 항목 수정, 확정, 거절",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>REVIEW 전용. 전달한 필드만 바꾼다. classification을 AUTO로 바꾸려면 testRef가 테스트 카탈로그에 있어야 한다. parts에서 빠진 파트의 셀은 NOT_APPLICABLE이 된다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 체크 항목 수정 성공"),
            @ApiResponse(responseCode = "400", description = "🚨 요청 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_4001\", \"message\": \"잘못된 요청 파라미터입니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "🚨 체크 항목 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"CHECKLIST_4041\", \"message\": \"존재하지 않는 체크 항목입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "🚨 REVIEW 상태가 아님, version 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SPRINT_4092", value = "{\"error\": \"SPRINT_4092\", \"message\": \"현재 스프린트 상태에서는 할 수 없는 작업입니다.\"}"),
                                    @ExampleObject(name = "GLOBAL_4091", value = "{\"error\": \"GLOBAL_4091\", \"message\": \"다른 사용자가 먼저 변경했습니다. 다시 불러온 뒤 시도하세요.\"}")
                            })),
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
    ResponseEntity<ChecklistItemResponse> updateChecklistItem(
            @Parameter(description = "체크 항목 ID") long itemId,
            ChecklistItemUpdateRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    );

    @Operation(
            summary = "수동 체크",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>IN_PROGRESS 전용. 허용 상태 UNCHECKED, PASS, FAIL. AUTO 항목의 WEB/SERVER 셀, NOT_APPLICABLE 셀, 거절된 항목의 셀은 바꿀 수 없다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 셀 변경 성공"),
            @ApiResponse(responseCode = "400", description = "🚨 요청 형식 오류, NOT_APPLICABLE로 변경 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "GLOBAL_4001", value = "{\"error\": \"GLOBAL_4001\", \"message\": \"잘못된 요청 파라미터입니다.\"}"),
                                    @ExampleObject(name = "CHECKLIST_4001", value = "{\"error\": \"CHECKLIST_4001\", \"message\": \"셀은 UNCHECKED, PASS, FAIL로만 바꿀 수 있습니다.\"}")
                            })),
            @ApiResponse(responseCode = "404", description = "🚨 체크 항목 또는 셀 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "CHECKLIST_4041", value = "{\"error\": \"CHECKLIST_4041\", \"message\": \"존재하지 않는 체크 항목입니다.\"}"),
                                    @ExampleObject(name = "CHECKLIST_4043", value = "{\"error\": \"CHECKLIST_4043\", \"message\": \"존재하지 않는 체크 셀입니다.\"}")
                            })),
            @ApiResponse(responseCode = "409", description = "🚨 IN_PROGRESS 상태가 아님, version 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SPRINT_4092", value = "{\"error\": \"SPRINT_4092\", \"message\": \"현재 스프린트 상태에서는 할 수 없는 작업입니다.\"}"),
                                    @ExampleObject(name = "GLOBAL_4091", value = "{\"error\": \"GLOBAL_4091\", \"message\": \"다른 사용자가 먼저 변경했습니다. 다시 불러온 뒤 시도하세요.\"}")
                            })),
            @ApiResponse(responseCode = "422", description = "🚨 수동으로 바꿀 수 없는 셀",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"CHECKLIST_4223\", \"message\": \"수동으로 바꿀 수 없는 셀입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<CheckCellResponse> updateCheckCell(
            @Parameter(description = "체크 항목 ID") long itemId,
            @Parameter(description = "파트") Part part,
            CheckCellUpdateRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    );

    @Operation(
            summary = "셀 변경 이력",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>최신순"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 셀 이력 조회 성공"),
            @ApiResponse(responseCode = "404", description = "🚨 셀 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"CHECKLIST_4043\", \"message\": \"존재하지 않는 체크 셀입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<List<CheckCellHistoryResponse>> listCheckCellHistory(
            @Parameter(description = "체크 항목 ID") long itemId,
            @Parameter(description = "파트") Part part
    );
}
