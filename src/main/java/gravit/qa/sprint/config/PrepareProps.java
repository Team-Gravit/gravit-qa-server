package gravit.qa.sprint.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("qa.prepare")
public record PrepareProps(
        @Min(1000) @DefaultValue("30000")
        int reflectionMaxPromptChars,

        @Min(1000) @DefaultValue("30000")
        int checklistMaxPromptChars,

        @Min(1000) @DefaultValue("60000")
        int designReviewMaxPromptChars,

        @Min(1000) @DefaultValue("80000")
        int autoReviewMaxPromptChars,

        @Min(1) @DefaultValue("4")
        int autoReviewMaxChunks
) {
}
