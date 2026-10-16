package gravit.qa.adapter.figma.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("qa.adapter.figma")
public record FigmaProps(
        @NotBlank
        String baseUrl,

        String token,

        @NotNull @DefaultValue("30s")
        Duration timeout
) {

    @Override
    public String toString() {
        return "FigmaProps[baseUrl=" + baseUrl + ", timeout=" + timeout + "]";
    }
}
