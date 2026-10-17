package gravit.qa.sprint.controller.docs;

import gravit.qa.global.exception.domain.ErrorResponse;
import gravit.qa.job.dto.response.JobResponse;
import gravit.qa.security.principal.LoginAdmin;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.request.SprintCloseRequest;
import gravit.qa.sprint.dto.request.SprintCreateRequest;
import gravit.qa.sprint.dto.response.SprintResponse;
import gravit.qa.sprint.dto.response.SprintSummaryResponse;
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

@Tag(name = "Sprint API", description = "스프린트 등록·조회·종료, QA 준비하기")
public interface SprintControllerDocs {

    @Operation(
            summary = "스프린트 QA 목록",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>최신 생성순. 상태로 거를 수 있다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 스프린트 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "🚨 잘못된 상태 값",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_4001\", \"message\": \"잘못된 요청 파라미터입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<List<SprintSummaryResponse>> listSprints(@Parameter(description = "스프린트 상태(생략 시 전체)") SprintStatus status);

    @Operation(
            summary = "Notion 스프린트 페이지로 스프린트 등록",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>Notion 스프린트 행을 읽어 DRAFT로 만든다. 기간이 비어 있어도 등록된다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "✅ 스프린트 등록 성공"),
            @ApiResponse(responseCode = "400", description = "🚨 요청 형식 오류, 스프린트 DB 행이 아님, Figma URL 형식 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "GLOBAL_4001", value = "{\"error\": \"GLOBAL_4001\", \"message\": \"잘못된 요청 파라미터입니다.\"}"),
                                    @ExampleObject(name = "SPRINT_4001", value = "{\"error\": \"SPRINT_4001\", \"message\": \"Notion 스프린트 DB의 페이지가 아니거나 스프린트 이름이 비어 있습니다.\"}"),
                                    @ExampleObject(name = "SPRINT_4002", value = "{\"error\": \"SPRINT_4002\", \"message\": \"Notion 'Figma QA 페이지' 값이 Figma 파일 URL이 아닙니다.\"}")
                            })),
            @ApiResponse(responseCode = "404", description = "🚨 Notion 페이지를 찾을 수 없음(Integration 미연결 포함)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4042\", \"message\": \"Notion 스프린트 페이지를 찾을 수 없습니다. 페이지에 Integration이 연결되어 있는지 확인하세요.\"}"))),
            @ApiResponse(responseCode = "409", description = "🚨 이미 등록된 Notion 페이지 또는 스프린트 이름",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4091\", \"message\": \"이미 등록된 Notion 스프린트입니다.\"}"))),
            @ApiResponse(responseCode = "502", description = "🚨 Notion 조회 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_5021\", \"message\": \"Notion에서 스프린트를 가져오지 못했습니다. 잠시 뒤 다시 시도하세요.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<SprintResponse> createSprint(
            SprintCreateRequest request,
            @AuthenticationPrincipal LoginAdmin loginAdmin
    );

    @Operation(
            summary = "스프린트 상세(기준점 포함)",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>화면 안내(guide)와 파트별 진행률을 함께 내려준다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 스프린트 조회 성공"),
            @ApiResponse(responseCode = "404", description = "🚨 스프린트 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4041\", \"message\": \"존재하지 않는 스프린트입니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<SprintResponse> getSprint(@Parameter(description = "스프린트 ID") long sprintId);

    @Operation(
            summary = "스프린트 종료",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>IN_PROGRESS에서만 가능. 미검수 셀 0, 실행 중 자동 실행 없음. FAIL은 허용한다. Notion QA 기준점 기록은 실패해도 종료한다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "✅ 스프린트 종료 성공"),
            @ApiResponse(responseCode = "404", description = "🚨 스프린트 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4041\", \"message\": \"존재하지 않는 스프린트입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "🚨 상태 불가, 미검수 셀 남음, 자동 실행 중, version 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SPRINT_4092", value = "{\"error\": \"SPRINT_4092\", \"message\": \"현재 스프린트 상태에서는 할 수 없는 작업입니다.\"}"),
                                    @ExampleObject(name = "SPRINT_4093", value = "{\"error\": \"SPRINT_4093\", \"message\": \"미검수 셀이 남아 있어 종료할 수 없습니다.\"}"),
                                    @ExampleObject(name = "SPRINT_4094", value = "{\"error\": \"SPRINT_4094\", \"message\": \"실행 중인 자동 실행이 있어 종료할 수 없습니다.\"}"),
                                    @ExampleObject(name = "GLOBAL_4091", value = "{\"error\": \"GLOBAL_4091\", \"message\": \"다른 사용자가 먼저 변경했습니다. 다시 불러온 뒤 시도하세요.\"}")
                            })),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<SprintResponse> closeSprint(
            @Parameter(description = "스프린트 ID") long sprintId,
            SprintCloseRequest request
    );

    @Operation(
            summary = "QA 준비하기 실행",
            description = "🔐 <strong>관리자 토큰 필요</strong><br>DRAFT, PREPARE_FAILED, REVIEW에서 가능. 스프린트를 PREPARING으로 바꾸고 SETUP 작업을 큐에 넣는다. REVIEW에서 다시 실행하면 성공 시 PROPOSED 항목만 교체된다"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "✅ 셋업 작업 생성"),
            @ApiResponse(responseCode = "404", description = "🚨 스프린트 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"SPRINT_4041\", \"message\": \"존재하지 않는 스프린트입니다.\"}"))),
            @ApiResponse(responseCode = "409", description = "🚨 상태 불가, 실행 중인 셋업 작업 있음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "SPRINT_4092", value = "{\"error\": \"SPRINT_4092\", \"message\": \"현재 스프린트 상태에서는 할 수 없는 작업입니다.\"}"),
                                    @ExampleObject(name = "JOB_4091", value = "{\"error\": \"JOB_4091\", \"message\": \"같은 스프린트에서 실행 중인 작업이 있습니다.\"}"),
                                    @ExampleObject(name = "GLOBAL_4091", value = "{\"error\": \"GLOBAL_4091\", \"message\": \"다른 사용자가 먼저 변경했습니다. 다시 불러온 뒤 시도하세요.\"}")
                            })),
            @ApiResponse(responseCode = "500", description = "🚨 예기치 못한 예외 발생",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"error\": \"GLOBAL_5001\", \"message\": \"예기치 못한 예외가 발생했습니다.\"}")))
    })
    ResponseEntity<JobResponse> startSetupJob(@Parameter(description = "스프린트 ID") long sprintId);
}
