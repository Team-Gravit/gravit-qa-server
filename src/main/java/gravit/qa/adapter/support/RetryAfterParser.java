package gravit.qa.adapter.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RetryAfterParser {

    private static final Pattern SECONDS = Pattern.compile("^\\d+(\\.\\d+)?$");

    public static Duration parse(
            HttpHeaders headers,
            Clock clock
    ) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (SECONDS.matcher(trimmed).matches()) {
            return Duration.ofMillis(new BigDecimal(trimmed).movePointRight(3).longValue());
        }
        try {
            ZonedDateTime retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            return nonNegative(Duration.between(clock.instant(), retryAt.toInstant()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static Duration nonNegative(Duration duration) {
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
