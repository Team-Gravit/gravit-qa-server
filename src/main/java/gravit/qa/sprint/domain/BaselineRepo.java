package gravit.qa.sprint.domain;

import gravit.qa.sprint.dto.internal.SetupBaselineRepoDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "baseline_repo")
@IdClass(BaselineRepoId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BaselineRepo {

    public static final String RELEASE_BRANCH = "release";

    @Id
    @Column(name = "baseline_id")
    private Long baselineId;

    @Id
    @Column(name = "repo")
    private String repo;

    @Column(name = "branch", nullable = false)
    private String branch;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSha;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "previous_commit_sha", length = 40)
    private String previousCommitSha;

    @Column(name = "aos_version_code")
    private Integer aosVersionCode;

    @Column(name = "aos_version_name", length = 100)
    private String aosVersionName;

    @Column(name = "version_code_warning", nullable = false)
    private boolean versionCodeWarning;

    @Builder(access = AccessLevel.PRIVATE)
    private BaselineRepo(
            Long baselineId,
            String repo,
            String branch,
            String commitSha,
            String previousCommitSha,
            Integer aosVersionCode,
            String aosVersionName,
            boolean versionCodeWarning
    ) {
        this.baselineId = baselineId;
        this.repo = repo;
        this.branch = branch;
        this.commitSha = commitSha;
        this.previousCommitSha = previousCommitSha;
        this.aosVersionCode = aosVersionCode;
        this.aosVersionName = aosVersionName;
        this.versionCodeWarning = versionCodeWarning;
    }

    public static BaselineRepo create(
            long baselineId,
            SetupBaselineRepoDto repo
    ) {
        return BaselineRepo.builder()
                .baselineId(baselineId)
                .repo(repo.repo())
                .branch(repo.branch())
                .commitSha(repo.commitSha())
                .previousCommitSha(repo.previousCommitSha())
                .aosVersionCode(repo.aosVersionCode())
                .aosVersionName(repo.aosVersionName())
                .versionCodeWarning(repo.versionCodeWarning())
                .build();
    }
}
