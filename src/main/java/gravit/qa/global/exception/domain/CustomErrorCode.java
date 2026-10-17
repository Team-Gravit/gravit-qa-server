package gravit.qa.global.exception.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Getter
@RequiredArgsConstructor
public enum CustomErrorCode implements ErrorCode {

    // Global
    INVALID_PARAMS(BAD_REQUEST, "GLOBAL_4001", "잘못된 요청 파라미터입니다."),
    RESOURCE_NOT_FOUND(NOT_FOUND, "GLOBAL_4041", "존재하지 않는 경로입니다."),
    VERSION_CONFLICT(CONFLICT, "GLOBAL_4091", "다른 사용자가 먼저 변경했습니다. 다시 불러온 뒤 시도하세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "GLOBAL_5001", "예기치 못한 예외가 발생했습니다."),
    DATABASE_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "GLOBAL_5002", "데이터베이스 처리 중 오류가 발생했습니다."),

    // Auth
    TOKEN_MISSING(UNAUTHORIZED, "AUTH_4011", "관리자 인증이 필요합니다."),
    TOKEN_INVALID(UNAUTHORIZED, "AUTH_4012", "토큰이 만료되었거나 유효하지 않습니다."),
    NOT_ADMIN(FORBIDDEN, "AUTH_4031", "Gravit 관리자만 사용할 수 있습니다."),
    AUTH_SERVER_UNAVAILABLE(SERVICE_UNAVAILABLE, "AUTH_5031", "인증 서버에서 관리자 여부를 확인할 수 없습니다."),

    // Job
    JOB_NOT_FOUND(NOT_FOUND, "JOB_4041", "존재하지 않는 작업입니다."),
    JOB_ALREADY_ACTIVE(CONFLICT, "JOB_4091", "같은 스프린트에서 실행 중인 작업이 있습니다."),
    JOB_NOT_CANCELLABLE(CONFLICT, "JOB_4092", "이미 종료된 작업은 취소할 수 없습니다."),

    // Sprint
    NOTION_SPRINT_PAGE_INVALID(BAD_REQUEST, "SPRINT_4001", "Notion 스프린트 DB의 페이지가 아니거나 스프린트 이름이 비어 있습니다."),
    SPRINT_FIGMA_URL_INVALID(BAD_REQUEST, "SPRINT_4002", "Notion 'Figma QA 페이지' 값이 Figma 파일 URL이 아닙니다."),
    SPRINT_NOT_FOUND(NOT_FOUND, "SPRINT_4041", "존재하지 않는 스프린트입니다."),
    NOTION_SPRINT_PAGE_NOT_FOUND(NOT_FOUND, "SPRINT_4042", "Notion 스프린트 페이지를 찾을 수 없습니다. 페이지에 Integration이 연결되어 있는지 확인하세요."),
    SPRINT_ALREADY_REGISTERED(CONFLICT, "SPRINT_4091", "이미 등록된 Notion 스프린트입니다."),
    SPRINT_STATUS_CONFLICT(CONFLICT, "SPRINT_4092", "현재 스프린트 상태에서는 할 수 없는 작업입니다."),
    SPRINT_UNCHECKED_CELLS_REMAIN(CONFLICT, "SPRINT_4093", "미검수 셀이 남아 있어 종료할 수 없습니다."),
    SPRINT_AUTO_RUN_IN_PROGRESS(CONFLICT, "SPRINT_4094", "실행 중인 자동 실행이 있어 종료할 수 없습니다."),
    NOTION_UNAVAILABLE(BAD_GATEWAY, "SPRINT_5021", "Notion에서 스프린트를 가져오지 못했습니다. 잠시 뒤 다시 시도하세요."),

    // Checklist
    CHECK_CELL_STATE_INVALID(BAD_REQUEST, "CHECKLIST_4001", "셀은 UNCHECKED, PASS, FAIL로만 바꿀 수 있습니다."),
    CHECKLIST_ITEM_NOT_FOUND(NOT_FOUND, "CHECKLIST_4041", "존재하지 않는 체크 항목입니다."),
    CHECKLIST_FEATURE_NOT_IN_SPRINT(NOT_FOUND, "CHECKLIST_4042", "스프린트 대상 기능이 아닙니다."),
    CHECK_CELL_NOT_FOUND(NOT_FOUND, "CHECKLIST_4043", "존재하지 않는 체크 셀입니다."),
    CHECKLIST_ITEMS_NOT_REVIEWED(CONFLICT, "CHECKLIST_4091", "검토하지 않은 체크 항목이 남아 있습니다."),
    TEST_REF_NOT_IN_CATALOG(UNPROCESSABLE_ENTITY, "CHECKLIST_4221", "테스트 카탈로그에 없는 testRef입니다. 자동 항목에는 카탈로그의 테스트가 있어야 합니다."),
    PERSONA_KEY_UNKNOWN(UNPROCESSABLE_ENTITY, "CHECKLIST_4222", "알 수 없는 페르소나 키입니다."),
    CHECK_CELL_LOCKED(UNPROCESSABLE_ENTITY, "CHECKLIST_4223", "수동으로 바꿀 수 없는 셀입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
