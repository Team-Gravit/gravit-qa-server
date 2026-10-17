package gravit.qa.sprint.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("qa.setup")
public record SetupProps(
        @Min(1000) @DefaultValue("30000")
        int maxPromptChars
) {
}
