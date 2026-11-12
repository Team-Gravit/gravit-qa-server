package gravit.qa.designReview.controller.docs;

import gravit.qa.designReview.dto.request.DesignReviewItemUpdateRequest;
import gravit.qa.designReview.dto.request.DesignReviewStateUpdateRequest;
import gravit.qa.designReview.dto.response.DesignReviewHistoryResponse;
import gravit.qa.designReview.dto.response.DesignReviewItemResponse;
import gravit.qa.designReview.dto.response.DesignReviewResponse;
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

@Tag(name = "Design Review API", description = "Figma 시안이 명세에 충실한지 본 디자인 검수 결과 조회, 변경, 수정, 삭제, 이력")
public interface DesignReviewControllerDocs {

    @Operation(
            summary = "디자인 검수 조회",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>화면별로 묶은 디자인 검수 항목. 항목 단위는 화면 1개 × 플랫폼 1개 안의 화면 영역이다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 디자인 검수 조회 성공"),
            @ApiResponse(responseCode = "404", description = "🚨 스프린트 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4041\", \"message\": \"존재하지 않는 스프린트입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<DesignReviewResponse> getDesignReview(@Parameter(description = "스프린트 ID") long sprintId);

    @Operation(
            summary = "디자인 검수 항목 상태 변경",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>MANUAL_REVIEW에서만 가능. UNCHECKED, PASS, FAIL, NOT_APPLICABLE(해당 없음)을 받고 이력을 남긴다. NOT_APPLICABLE은 진행도 total에서 빠지고 종료를 막지 않는다. FAIL로 바꿀 때 reason(선택)을 남길 수 있다. 상태가 같으면 바꾸지 않되, 이미 FAIL인 항목에 다른 reason을 보내면 사유만 바꾼다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 디자인 검수 항목 변경 성공"),
            @ApiResponse(responseCode = "400", description = "🚨 reason 500자 초과, 요청 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_4001\", \"message\": \"잘못된 요청 파라미터입니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "🚨 디자인 검수 항목 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"DESIGN_4041\", \"message\": \"존재하지 않는 디자인 검수 항목입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "🚨 MANUAL_REVIEW가 아님, version 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SPRINT_4092", value = "{\"error\": \"SPRINT_4092\", \"message\": \"현재 스프린트 상태에서는 할 수 없는 작업입니다.\"}"),
                                    @ExampleObject(name = "GLOBAL_4091", value = "{\"error\": \"GLOBAL_4091\", \"message\": \"다른 사용자가 먼저 변경했습니다. 다시 불러온 뒤 시도하세요.\"}")
                            })),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<DesignReviewItemResponse> updateDesignReviewItemState(
            @Parameter(description = "디자인 검수 항목 ID") long itemId,
            DesignReviewStateUpdateRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    );

    @Operation(
            summary = "디자인 검수 항목 수정",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>MANUAL_REVIEW이고 실행 중인 작업이 없을 때 가능. 전달한 필드만 바꾼다. 행위나 설명이 바뀌면 상태를 UNCHECKED, source를 MANUAL로 되돌리고 이력 사유 \"항목 수정으로 판정 초기화\"를 남긴다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 디자인 검수 항목 수정 성공"),
            @ApiResponse(responseCode = "400", description = "🚨 요청 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_4001\", \"message\": \"잘못된 요청 파라미터입니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "🚨 디자인 검수 항목 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"DESIGN_4041\", \"message\": \"존재하지 않는 디자인 검수 항목입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "🚨 MANUAL_REVIEW가 아님, 실행 중인 작업 있음, version 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SPRINT_4092", value = "{\"error\": \"SPRINT_4092\", \"message\": \"현재 스프린트 상태에서는 할 수 없는 작업입니다.\"}"),
                                    @ExampleObject(name = "GLOBAL_4091", value = "{\"error\": \"GLOBAL_4091\", \"message\": \"다른 사용자가 먼저 변경했습니다. 다시 불러온 뒤 시도하세요.\"}")
                            })),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<DesignReviewItemResponse> updateDesignReviewItem(
            @Parameter(description = "디자인 검수 항목 ID") long itemId,
            DesignReviewItemUpdateRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    );

    @Operation(
            summary = "디자인 검수 항목 삭제",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>MANUAL_REVIEW이고 실행 중인 작업이 없을 때 가능. 항목 이력도 함께 지운다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "✅ 디자인 검수 항목 삭제 성공"),
            @ApiResponse(responseCode = "400", description = "🚨 version 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_4001\", \"message\": \"잘못된 요청 파라미터입니다.\"}"))),
            @ApiResponse(responseCode = "404", description = "🚨 디자인 검수 항목 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"DESIGN_4041\", \"message\": \"존재하지 않는 디자인 검수 항목입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "🚨 MANUAL_REVIEW가 아님, 실행 중인 작업 있음, version 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SPRINT_4092", value = "{\"error\": \"SPRINT_4092\", \"message\": \"현재 스프린트 상태에서는 할 수 없는 작업입니다.\"}"),
                                    @ExampleObject(name = "GLOBAL_4091", value = "{\"error\": \"GLOBAL_4091\", \"message\": \"다른 사용자가 먼저 변경했습니다. 다시 불러온 뒤 시도하세요.\"}")
                            })),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<Void> deleteDesignReviewItem(
            @Parameter(description = "디자인 검수 항목 ID") long itemId,
            @Parameter(description = "디자인 검수 항목 낙관적 잠금 값") Integer version
    );

    @Operation(
            summary = "디자인 검수 항목 이력",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>최신순. 자동 비교(source=AUTO)와 사람 변경을 함께 내려준다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 이력 조회 성공"),
            @ApiResponse(responseCode = "404", description = "🚨 디자인 검수 항목 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"DESIGN_4041\", \"message\": \"존재하지 않는 디자인 검수 항목입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<List<DesignReviewHistoryResponse>> listDesignReviewItemHistory(@Parameter(description = "디자인 검수 항목 ID") long itemId);
}
