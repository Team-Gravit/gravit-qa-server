package gravit.qa.global.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("qa.storage")
public record StorageProps(
        @NotBlank String root
) {
}
