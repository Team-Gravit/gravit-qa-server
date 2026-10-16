package gravit.qa.adapter.exception;

import gravit.qa.job.support.NonRetryableJobException;
import lombok.Getter;

import java.util.List;

@Getter
public class LlmOutputInvalidException extends NonRetryableJobException {

    private static final String ERROR_DELIMITER = "; ";

    private final List<String> errors;

    public LlmOutputInvalidException(
            int attempts,
            List<String> errors
    ) {
        super("LLM 출력이 " + attempts + "회 모두 출력 계약을 지키지 않았습니다: " + String.join(ERROR_DELIMITER, errors));
        this.errors = List.copyOf(errors);
    }
}
