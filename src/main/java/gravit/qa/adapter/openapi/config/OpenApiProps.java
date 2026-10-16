package gravit.qa.adapter.openapi.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("qa.adapter.openapi")
public record OpenApiProps(
        @NotBlank
        String url,

        @NotNull @DefaultValue("30s")
        Duration timeout
) {
}
