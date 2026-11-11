package gravit.qa.autoReview.dto.internal;

import java.util.List;
import java.util.Map;

public record CodeSelectionDto(
        List<SourceFileDto> files,
        Map<String, String> repoToCommitSha
) {

    public static CodeSelectionDto of(
            List<SourceFileDto> files,
            Map<String, String> repoToCommitSha
    ) {
        return new CodeSelectionDto(List.copyOf(files), Map.copyOf(repoToCommitSha));
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }
}
