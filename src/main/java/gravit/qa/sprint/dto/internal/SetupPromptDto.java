package gravit.qa.sprint.dto.internal;

import gravit.qa.adapter.llm.dto.internal.LlmPromptDto;

public record SetupPromptDto(
        LlmPromptDto prompt,
        boolean truncated
) {

    public static SetupPromptDto of(
            LlmPromptDto prompt,
            boolean truncated
    ) {
        return new SetupPromptDto(prompt, truncated);
    }
}
