package gravit.qa.global.exception.domain;

public record ErrorResponse<T>(
        String error,
        T message
) {

    public static <T> ErrorResponse<T> of(
            String error,
            T message
    ) {
        return new ErrorResponse<>(error, message);
    }
}
