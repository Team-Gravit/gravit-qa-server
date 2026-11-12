package gravit.qa.adapter.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("qa.adapter.retry")
public record AdapterRetryProps(
        @Min(0) @DefaultValue("3")
        int maxRetries,

        @NotNull @DefaultValue("1s")
        Duration backoffBase,

        @NotNull @DefaultValue("60s")
        Duration maxWait
) {

    public Duration backoff(int retry) {
        return backoffBase.multipliedBy(1L << retry);
    }
}
