package gravit.qa.global.exception.handler;

import gravit.qa.global.exception.domain.ErrorCode;
import gravit.qa.global.exception.domain.ErrorResponse;
import gravit.qa.global.exception.domain.RestApiException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static gravit.qa.global.exception.domain.CustomErrorCode.DATABASE_EXCEPTION;
import static gravit.qa.global.exception.domain.CustomErrorCode.INTERNAL_SERVER_ERROR;
import static gravit.qa.global.exception.domain.CustomErrorCode.INVALID_PARAMS;
import static gravit.qa.global.exception.domain.CustomErrorCode.RESOURCE_NOT_FOUND;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RestApiException.class)
    public ResponseEntity<ErrorResponse<String>> handleRestApiException(RestApiException e) {
        return toResponse(e.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse<List<String>>> handleValidException(MethodArgumentNotValidException e) {
        List<String> messages = e.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldMessage)
                .toList();

        return toResponse(messages);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse<List<String>>> handleConstraintViolationException(ConstraintViolationException e) {
        List<String> messages = e.getConstraintViolations().stream()
                .map(violation -> "[ " + violation.getPropertyPath() + " ][ " + violation.getMessage() + " ]")
                .toList();

        return toResponse(messages);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse<String>> handleBadRequest(Exception e) {
        return toResponse(INVALID_PARAMS);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse<String>> handleNoResource(NoResourceFoundException e) {
        return toResponse(RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse<String>> handleDatabaseException(DataAccessException e) {
        log.error("DataAccessException occur with: {}", e.getMessage());

        return toResponse(DATABASE_EXCEPTION);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse<String>> handleException(Exception e) {
        log.error("예외 발생 :", e);

        return toResponse(INTERNAL_SERVER_ERROR);
    }

    private String toFieldMessage(FieldError error) {
        return "[ " + error.getField() + " ][ " + error.getDefaultMessage() + " ][ " + error.getRejectedValue() + " ]";
    }

    private ResponseEntity<ErrorResponse<String>> toResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode.getCode(), errorCode.getMessage()));
    }

    private ResponseEntity<ErrorResponse<List<String>>> toResponse(List<String> messages) {
        return ResponseEntity.status(INVALID_PARAMS.getHttpStatus())
                .body(ErrorResponse.of(INVALID_PARAMS.getCode(), messages));
    }
}
