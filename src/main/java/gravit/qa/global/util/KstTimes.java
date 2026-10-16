package gravit.qa.global.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static java.time.temporal.ChronoUnit.MICROS;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KstTimes {

    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock.withZone(TimeZoneConst.KST)).truncatedTo(MICROS);
    }

    public static OffsetDateTime toOffset(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(TimeZoneConst.KST).toOffsetDateTime();
    }
}
