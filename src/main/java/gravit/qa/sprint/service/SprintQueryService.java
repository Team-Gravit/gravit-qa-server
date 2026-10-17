package gravit.qa.sprint.service;

import gravit.qa.adapter.github.config.GithubProps;
import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.global.exception.domain.RestApiException;
import gravit.qa.sprint.domain.Baseline;
import gravit.qa.sprint.domain.BaselineRepo;
import gravit.qa.sprint.domain.Sprint;
import gravit.qa.sprint.domain.SprintStatus;
import gravit.qa.sprint.dto.internal.PreviousBaselineDto;
import gravit.qa.sprint.dto.internal.SetupTargetDto;
import gravit.qa.sprint.dto.internal.SprintDto;
import gravit.qa.sprint.dto.response.BaselineRepoResponse;
import gravit.qa.sprint.dto.response.BaselineResponse;
import gravit.qa.sprint.repository.BaselineRepoRepository;
import gravit.qa.sprint.repository.BaselineRepository;
import gravit.qa.sprint.repository.SprintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static gravit.qa.global.exception.domain.CustomErrorCode.SPRINT_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class SprintQueryService {

    private static final String GITHUB_WEB_URL = "https://github.com/";
    private static final String PATH_SEPARATOR = "/";
    private static final String COMPARE_PATH = "/compare/";
    private static final String COMPARE_RANGE = "...";
    private static final String SUMMARY_LINE = "%s %s %s";
    private static final String AOS_VERSION_SUFFIX = " (versionCode %d, versionName %s)";
    private static final String LINE_BREAK = "\n";
    private static final String AOS_REPO = GithubRepo.GRAVIT_AOS.getRepoName();

    private final SprintRepository sprintRepository;
    private final BaselineRepository baselineRepository;
    private final BaselineRepoRepository baselineRepoRepository;

    private final GithubProps githubProps;

    @Transactional(readOnly = true)
    public SprintDto getSprint(long sprintId) {
        return SprintDto.from(findSprint(sprintId));
    }

    @Transactional(readOnly = true)
    public List<SprintDto> getSprints(SprintStatus status) {
        List<Sprint> sprints = status == null
                ? sprintRepository.findAllByOrderByCreatedAtDescIdDesc()
                : sprintRepository.findAllByStatusOrderByCreatedAtDescIdDesc(status);

        return sprints.stream().map(SprintDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Optional<BaselineResponse> findBaseline(long sprintId) {
        return baselineRepository.findBySprintId(sprintId)
                .map(baseline -> BaselineResponse.of(
                        baseline,
                        baselineRepoRepository.findAllByBaselineIdOrderByRepo(baseline.getId()).stream()
                                .map(repo -> BaselineRepoResponse.of(repo, compareUrl(repo)))
                                .toList()
                ));
    }

    @Transactional(readOnly = true)
    public SetupTargetDto getSetupTarget(long sprintId) {
        return SetupTargetDto.from(findSprint(sprintId));
    }

    @Transactional(readOnly = true)
    public Optional<PreviousBaselineDto> findPreviousClosedBaseline(long sprintId) {
        return baselineRepository.findClosedBefore(sprintId, SprintStatus.CLOSED, Limit.of(1)).stream()
                .findFirst()
                .map(this::toPreviousBaseline);
    }

    @Transactional(readOnly = true)
    public long findBaselineId(long sprintId) {
        return baselineRepository.findBySprintId(sprintId)
                .map(Baseline::getId)
                .orElseThrow(() -> new IllegalStateException("기준점이 없는 스프린트입니다. sprintId=" + sprintId));
    }

    @Transactional(readOnly = true)
    public String getNotionBaselineSummary(long sprintId) {
        Baseline baseline = baselineRepository.findBySprintId(sprintId)
                .orElseThrow(() -> new IllegalStateException("기준점이 없는 스프린트입니다. sprintId=" + sprintId));

        return baselineRepoRepository.findAllByBaselineIdOrderByRepo(baseline.getId()).stream()
                .map(this::toSummaryLine)
                .collect(Collectors.joining(LINE_BREAK));
    }

    private Sprint findSprint(long sprintId) {
        return sprintRepository.findById(sprintId)
                .orElseThrow(() -> new RestApiException(SPRINT_NOT_FOUND));
    }

    private PreviousBaselineDto toPreviousBaseline(Baseline baseline) {
        List<BaselineRepo> repos = baselineRepoRepository.findAllByBaselineIdOrderByRepo(baseline.getId());

        Map<String, String> repoToCommitSha = new LinkedHashMap<>();
        repos.forEach(repo -> repoToCommitSha.put(repo.getRepo(), repo.getCommitSha()));
        Integer aosVersionCode = repos.stream()
                .filter(repo -> AOS_REPO.equals(repo.getRepo()))
                .map(BaselineRepo::getAosVersionCode)
                .findFirst()
                .orElse(null);

        return PreviousBaselineDto.of(baseline.getId(), repoToCommitSha, aosVersionCode);
    }

    private String compareUrl(BaselineRepo repo) {
        if (repo.getPreviousCommitSha() == null) {
            return null;
        }
        return GITHUB_WEB_URL + githubProps.owner() + PATH_SEPARATOR + repo.getRepo()
                + COMPARE_PATH + repo.getPreviousCommitSha() + COMPARE_RANGE + repo.getCommitSha();
    }

    private String toSummaryLine(BaselineRepo repo) {
        String line = SUMMARY_LINE.formatted(repo.getRepo(), repo.getBranch(), repo.getCommitSha());
        if (repo.getAosVersionCode() == null) {
            return line;
        }
        return line + AOS_VERSION_SUFFIX.formatted(repo.getAosVersionCode(), repo.getAosVersionName());
    }
}
