package gravit.qa.global.exception.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Getter
@RequiredArgsConstructor
public enum CustomErrorCode implements ErrorCode {

    // Global
    INVALID_PARAMS(BAD_REQUEST, "GLOBAL_4001", "잘못된 요청 파라미터입니다."),
    RESOURCE_NOT_FOUND(NOT_FOUND, "GLOBAL_4041", "존재하지 않는 경로입니다."),
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
    JOB_NOT_CANCELLABLE(CONFLICT, "JOB_4092", "이미 종료된 작업은 취소할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
