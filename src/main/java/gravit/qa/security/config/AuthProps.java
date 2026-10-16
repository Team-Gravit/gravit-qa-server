package gravit.qa.security.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("qa.auth")
public record AuthProps(
        @NotBlank String host,

        @NotNull @DefaultValue("5s") Duration timeout,

        @NotNull @DefaultValue("300s") Duration cacheTtl
) {
}
