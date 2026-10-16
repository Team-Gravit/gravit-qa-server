package gravit.qa.adapter.github.dto.internal;

public record GithubChangedFileDto(
        String filename,
        String status,
        int additions,
        int deletions,
        int changes,
        String patch,
        String previousFilename
) {
}
