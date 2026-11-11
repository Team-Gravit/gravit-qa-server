package gravit.qa.autoReview.dto.internal;

import gravit.qa.adapter.github.domain.GithubRepo;

import java.util.List;

public record SourceFileDto(
        GithubRepo repo,
        String path,
        List<String> lines,
        int firstLine
) {

    public static SourceFileDto of(
            GithubRepo repo,
            String path,
            List<String> lines,
            int firstLine
    ) {
        return new SourceFileDto(repo, path, List.copyOf(lines), firstLine);
    }

    public String label() {
        return repo.getRepoName() + "/" + path;
    }

    public int lastLine() {
        return firstLine + lines.size() - 1;
    }
}
