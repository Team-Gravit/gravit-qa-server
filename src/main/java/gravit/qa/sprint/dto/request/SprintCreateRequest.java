package gravit.qa.sprint.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record SprintCreateRequest(
        @NotBlank
        @Schema(
                description = "Notion 스프린트 DB 행 페이지 URL",
                example = "https://www.notion.so/3rd-Agile-1234567890abcdef1234567890abcdef",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String notionPageUrl
) {
}
