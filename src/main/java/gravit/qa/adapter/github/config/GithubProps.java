package gravit.qa.adapter.github.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("qa.adapter.github")
public record GithubProps(
        @NotBlank String baseUrl,
        String token,

        @NotBlank
        String owner,

        @NotNull @DefaultValue("10s")
        Duration timeout
) {

    @Override
    public String toString() {
        return "GithubProps[baseUrl=" + baseUrl + ", owner=" + owner + ", timeout=" + timeout + "]";
    }
}
