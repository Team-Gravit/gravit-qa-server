package gravit.qa.reflection.controller.docs;

import gravit.qa.global.exception.domain.ErrorResponse;
import gravit.qa.reflection.dto.request.ReflectionDecisionRequest;
import gravit.qa.reflection.dto.response.ReflectionResponse;
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

@Tag(name = "Reflection API", description = "수정 이력 반영 제안 조회, 승인(Notion 반영), 거절")
public interface ReflectionControllerDocs {

    @Operation(
            summary = "반영 제안 목록",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>이번 스프린트 수정 이력 행을 명세 본문에 반영하는 제안. before, after는 본문 마크다운이다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 반영 제안 조회 성공"),
            @ApiResponse(responseCode = "404", description = "🚨 스프린트 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4041\", \"message\": \"존재하지 않는 스프린트입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<List<ReflectionResponse>> listReflections(@Parameter(description = "스프린트 ID") long sprintId);

    @Operation(
            summary = "반영 제안 승인",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>SPEC_LOAD에서 실행 중인 명세 로드 작업이 없을 때 가능. Notion 본문에 반영하고 수정 이력 행의 반영일을 기록한다. 실패하면 FAILED와 message를 남기고, 다시 승인하면 남은 편집부터 이어서 한다. 모든 제안을 처리하면 체크리스트 생성을 시작한다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 반영 성공"),
            @ApiResponse(responseCode = "404", description = "🚨 반영 제안 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"REFLECTION_4041\", \"message\": \"존재하지 않는 반영 제안입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "🚨 지금 상태에서 할 수 없음, 제안 이후 명세가 바뀜, version 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SPRINT_4092", value = "{\"error\": \"SPRINT_4092\", \"message\": \"현재 스프린트 상태에서는 할 수 없는 작업입니다.\"}"),
                                    @ExampleObject(name = "REFLECTION_4091", value = "{\"error\": \"REFLECTION_4091\", \"message\": \"제안을 만든 뒤 Notion 명세가 바뀌었습니다. 명세를 다시 수집하세요.\"}"),
                                    @ExampleObject(name = "GLOBAL_4091", value = "{\"error\": \"GLOBAL_4091\", \"message\": \"다른 사용자가 먼저 변경했습니다. 다시 불러온 뒤 시도하세요.\"}")
                            })),
            @ApiResponse(responseCode = "502", description = "🚨 Notion 반영 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"REFLECTION_5021\", \"message\": \"Notion 명세를 읽거나 반영하지 못했습니다. 잠시 뒤 다시 시도하세요.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<ReflectionResponse> approveReflection(
            @Parameter(description = "반영 제안 ID") long reflectionId,
            ReflectionDecisionRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    );

    @Operation(
            summary = "반영 제안 거절",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>반영하지 않고 지금 본문을 기준 명세로 쓴다. 모든 제안을 처리하면 체크리스트 생성을 시작한다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 거절 성공"),
            @ApiResponse(responseCode = "404", description = "🚨 반영 제안 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"REFLECTION_4041\", \"message\": \"존재하지 않는 반영 제안입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "🚨 지금 상태에서 할 수 없음, version 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SPRINT_4092", value = "{\"error\": \"SPRINT_4092\", \"message\": \"현재 스프린트 상태에서는 할 수 없는 작업입니다.\"}"),
                                    @ExampleObject(name = "GLOBAL_4091", value = "{\"error\": \"GLOBAL_4091\", \"message\": \"다른 사용자가 먼저 변경했습니다. 다시 불러온 뒤 시도하세요.\"}")
                            })),
            @ApiResponse(responseCode = "502", description = "🚨 일부 반영된 명세를 다시 읽지 못함",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"REFLECTION_5021\", \"message\": \"Notion 명세를 읽거나 반영하지 못했습니다. 잠시 뒤 다시 시도하세요.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<ReflectionResponse> rejectReflection(
            @Parameter(description = "반영 제안 ID") long reflectionId,
            ReflectionDecisionRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    );
}
