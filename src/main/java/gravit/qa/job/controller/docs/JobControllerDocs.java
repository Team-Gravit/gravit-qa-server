package gravit.qa.job.controller.docs;

import gravit.qa.global.exception.domain.ErrorResponse;
import gravit.qa.job.dto.response.JobResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Job API", description = "작업 상태 조회·취소")
public interface JobControllerDocs {

    @Operation(
            summary = "작업 상태·단계 조회",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>진행 화면의 polling 대상"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 작업 조회 성공"),
            @ApiResponse(responseCode = "404", description = "🚨 작업 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"JOB_4041\", \"message\": \"존재하지 않는 작업입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<JobResponse> getJob(@Parameter(description = "작업 ID") long jobId);

    @Operation(
            summary = "작업 취소 요청",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>대기 중이면 즉시 취소, 실행 중이면 다음 단계 경계에서 중단"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "✅ 취소 요청 접수"),
            @ApiResponse(responseCode = "404", description = "🚨 작업 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"JOB_4041\", \"message\": \"존재하지 않는 작업입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "🚨 이미 종료된 작업",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"JOB_4092\", \"message\": \"이미 종료된 작업은 취소할 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<JobResponse> cancelJob(@Parameter(description = "작업 ID") long jobId);
}
