package gravit.qa.adapter.llm.dto.internal;

import java.util.List;

public record LlmValidationResultDto<T>(
        T value,
        List<String> errors
) {

    public static <T> LlmValidationResultDto<T> valid(T value) {
        return new LlmValidationResultDto<>(value, List.of());
    }

    public static <T> LlmValidationResultDto<T> invalid(List<String> errors) {
        return new LlmValidationResultDto<>(null, List.copyOf(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
