package gravit.qa.adapter.llm.support;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.adapter.llm.dto.internal.LlmValidationResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class LlmOutputValidatorTest {

    private final LlmOutputValidator validator = new LlmOutputValidator(new ObjectMapper());

    @Nested
    @DisplayName("@Nullable 컴포넌트가 있는 출력 타입이면")
    class Nullable_컴포넌트 {

        @Test
        void Nullable_필드는_null을_허용한다() {
            // given
            LlmOutputSchema<OutputDto> schema = validator.schemaOf(OutputDto.class);

            // when
            LlmValidationResultDto<OutputDto> result = validator.validate(
                    "{\"items\": [{\"title\": \"제목\", \"persona\": null, \"detail\": {\"reason\": \"근거\", \"note\": null}}]}",
                    schema,
                    value -> List.of()
            );

            // then
            assertSoftly(softly -> {
                softly.assertThat(result.isValid()).isTrue();
                softly.assertThat(result.value().items().getFirst().persona()).isNull();
                softly.assertThat(result.value().items().getFirst().detail().note()).isNull();
            });
        }

        @Test
        void Nullable이_아닌_필드는_null을_거부한다() {
            // given
            LlmOutputSchema<OutputDto> schema = validator.schemaOf(OutputDto.class);

            // when
            LlmValidationResultDto<OutputDto> result = validator.validate(
                    "{\"items\": [{\"title\": null, \"persona\": \"learner\", \"detail\": {\"reason\": \"근거\", \"note\": \"메모\"}}]}",
                    schema,
                    value -> List.of()
            );

            // then
            assertThat(result.errors()).containsExactly("/items/0/title: null 발견, string 예상");
        }

        @Test
        void LLM에_보내는_형식_안내의_스키마에도_null_허용이_들어간다() {
            // when
            LlmOutputSchema<OutputDto> schema = validator.schemaOf(OutputDto.class);

            // then
            assertThat(schema.format()).contains("[ \"string\", \"null\" ]");
        }
    }

    public record OutputDto(
            @JsonProperty(required = true)
            List<ItemDto> items
    ) {
    }

    public record ItemDto(
            @JsonProperty(required = true)
            String title,

            @Nullable
            @JsonProperty(required = true)
            String persona,

            @JsonProperty(required = true)
            DetailDto detail
    ) {
    }

    public record DetailDto(
            @JsonProperty(required = true)
            String reason,

            @Nullable
            @JsonProperty(required = true)
            String note
    ) {
    }
}
