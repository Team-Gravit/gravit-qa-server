package gravit.qa.job.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("qa.job")
public record JobProps(
        @Min(1) @DefaultValue("4") int maxAttempts,

        @NotNull @DefaultValue("2m") Duration backoffBase,

        @NotNull @DefaultValue("2m") Duration heartbeatTimeout
) {
}
