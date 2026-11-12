package gravit.qa.adapter.llm.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("qa.adapter.llm")
public record LlmProps(
        @NotBlank
        String baseUrl,

        String apiKey,

        @NotBlank
        String model,

        @NotNull @DefaultValue("120s")
        Duration timeout,

        @Min(1) @DefaultValue("5")
        int maxRequestsPerMinute,

        @Min(0) @DefaultValue("2")
        int maxOutputRetries,

        @Min(1) @DefaultValue("2")
        int maxConsecutiveRateLimits
) {

    @Override
    public String toString() {
        return "LlmProps[baseUrl=" + baseUrl + ", model=" + model + ", timeout=" + timeout
                + ", maxRequestsPerMinute=" + maxRequestsPerMinute + ", maxOutputRetries=" + maxOutputRetries
                + ", maxConsecutiveRateLimits=" + maxConsecutiveRateLimits + "]";
    }
}
