package gravit.qa.global.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZoneId;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TimeZoneConst {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
}
