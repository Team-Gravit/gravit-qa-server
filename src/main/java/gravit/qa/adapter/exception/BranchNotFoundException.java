package gravit.qa.adapter.exception;

import gravit.qa.adapter.domain.ExternalApi;
import gravit.qa.adapter.github.domain.GithubRepo;
import lombok.Getter;

@Getter
public class BranchNotFoundException extends NonRetryableExternalApiException {

    private static final int STATUS_NOT_FOUND = 404;

    private final GithubRepo repo;
    private final String branch;

    public BranchNotFoundException(
            GithubRepo repo,
            String branch
    ) {
        super(ExternalApi.GITHUB, STATUS_NOT_FOUND, repo.getRepoName() + " 저장소에 " + branch + " 브랜치가 없습니다.");
        this.repo = repo;
        this.branch = branch;
    }
}
