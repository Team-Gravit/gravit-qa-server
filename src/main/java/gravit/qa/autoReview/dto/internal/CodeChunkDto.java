package gravit.qa.autoReview.dto.internal;

import java.util.List;

public record CodeChunkDto(
        List<SourceFileDto> slices,
        String rendered
) {
}
