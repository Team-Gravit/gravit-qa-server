package gravit.qa.sprint.dto.response;

import gravit.qa.sprint.domain.BaselineRepo;
import io.swagger.v3.oas.annotations.media.Schema;

public record BaselineRepoResponse(
        @Schema(
                description = "저장소",
                example = "gravit-server",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String repo,

        @Schema(
                description = "브랜치",
                example = "release",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String branch,

        @Schema(
                description = "준비 시점 HEAD 커밋 SHA",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String commitSha,

        @Schema(description = "직전 기준점 SHA. null이면 직전 기준점 없음(전체 범위)")
        String previousCommitSha,

        @Schema(description = "GitHub compare URL")
        String compareUrl,

        @Schema(description = "gravit-aos만")
        Integer aosVersionCode,

        @Schema(description = "gravit-aos만")
        String aosVersionName,

        @Schema(
                description = "versionCode가 직전 기준점보다 크지 않음",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        boolean versionCodeWarning
) {

    public static BaselineRepoResponse of(
            BaselineRepo repo,
            String compareUrl
    ) {
        return new BaselineRepoResponse(
                repo.getRepo(),
                repo.getBranch(),
                repo.getCommitSha(),
                repo.getPreviousCommitSha(),
                compareUrl,
                repo.getAosVersionCode(),
                repo.getAosVersionName(),
                repo.isVersionCodeWarning()
        );
    }
}
