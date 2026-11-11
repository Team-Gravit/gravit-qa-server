package gravit.qa.adapter.github.dto.internal;

public record GithubTreeEntryDto(
        String path,
        String blobSha,
        long size
) {
}
