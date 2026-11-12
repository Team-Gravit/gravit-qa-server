package gravit.qa.adapter.notion.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("qa.adapter.notion")
public record NotionProps(
        @NotBlank
        String baseUrl,

        String token,

        @NotBlank
        String version,

        @NotBlank
        String sprintDatabaseId,

        @NotBlank
        String specDatabaseId,

        @NotNull @DefaultValue("30s")
        Duration timeout
) {

    @Override
    public String toString() {
        return "NotionProps[baseUrl=" + baseUrl + ", version=" + version + ", sprintDatabaseId=" + sprintDatabaseId
                + ", specDatabaseId=" + specDatabaseId + ", timeout=" + timeout + "]";
    }
}
