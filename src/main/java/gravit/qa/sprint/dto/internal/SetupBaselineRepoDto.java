package gravit.qa.sprint.dto.internal;

public record SetupBaselineRepoDto(
        String repo,
        String branch,
        String commitSha,
        String previousCommitSha,
        Integer aosVersionCode,
        String aosVersionName,
        boolean versionCodeWarning
) {

    public static SetupBaselineRepoDto of(
            String repo,
            String branch,
            String commitSha,
            String previousCommitSha,
            Integer aosVersionCode,
            String aosVersionName,
            boolean versionCodeWarning
    ) {
        return new SetupBaselineRepoDto(repo, branch, commitSha, previousCommitSha, aosVersionCode, aosVersionName, versionCodeWarning);
    }
}
