package gravit.qa.support;

import gravit.qa.global.util.TimeZoneConst;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.OffsetDateTime;

@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    public static final Instant BASE_INSTANT = OffsetDateTime.parse("2026-10-16T12:00:00+09:00").toInstant();

    @Bean
    @Primary
    public TestClock testClock() {
        return new TestClock(BASE_INSTANT, TimeZoneConst.KST);
    }
}
