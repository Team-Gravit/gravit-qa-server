package gravit.qa.autoReview.support;

import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.autoReview.dto.internal.ApiEndpointDto;
import gravit.qa.autoReview.dto.internal.CodeSelectionDto;
import gravit.qa.autoReview.dto.internal.RepoSnapshotDto;
import gravit.qa.autoReview.dto.internal.SourceFileDto;
import gravit.qa.checklist.dto.internal.ChecklistItemDto;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.dto.internal.FeatureDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CodeSelector {

    private static final String LINE_BREAK = "\n";
    private static final String OAUTH_PATH = "/oauth";
    private static final List<String> SHELL_KEYWORDS = List.of("로그인", "뒤로", "딥링크", "OAuth", "WebView", "웹뷰");

    private final RepoSourceReader repoSourceReader;

    public CodeSelectionDto select(
            Part part,
            FeatureDto feature,
            List<ChecklistItemDto> items,
            Map<GithubRepo, String> repoToCommitSha
    ) {
        List<ApiEndpointDto> endpoints = ApiPaths.parseAll(feature.relatedApis());
        return switch (part) {
            case SERVER -> {
                RepoSnapshotDto server = repoSourceReader.tree(GithubRepo.GRAVIT_SERVER, repoToCommitSha.get(GithubRepo.GRAVIT_SERVER));
                List<String> paths = ServerCodeSelector.select(server.pathToEntry().keySet(), path -> repoSourceReader.read(server, path), endpoints);
                yield selection(List.of(server), wholeFiles(server, paths));
            }
            case WEB -> {
                RepoSnapshotDto web = repoSourceReader.tree(GithubRepo.GRAVIT_WEB, repoToCommitSha.get(GithubRepo.GRAVIT_WEB));
                List<String> paths = WebCodeSelector.select(web.pathToEntry().keySet(), path -> repoSourceReader.read(web, path), feature.route());
                yield selection(List.of(web), wholeFiles(web, paths));
            }
            case APP -> {
                RepoSnapshotDto aos = repoSourceReader.tree(GithubRepo.GRAVIT_AOS, repoToCommitSha.get(GithubRepo.GRAVIT_AOS));
                RepoSnapshotDto web = repoSourceReader.tree(GithubRepo.GRAVIT_WEB, repoToCommitSha.get(GithubRepo.GRAVIT_WEB));
                List<SourceFileDto> files = AppCodeSelector.select(
                        aos.pathToEntry().keySet(),
                        path -> repoSourceReader.read(aos, path),
                        web.pathToEntry().keySet(),
                        path -> repoSourceReader.read(web, path),
                        endpoints,
                        needsShell(feature, items)
                );
                yield selection(List.of(aos, web), files);
            }
        };
    }

    private boolean needsShell(
            FeatureDto feature,
            List<ChecklistItemDto> items
    ) {
        if (feature.relatedApis().stream().anyMatch(api -> api.contains(OAUTH_PATH))) {
            return true;
        }
        return items.stream().anyMatch(item -> SHELL_KEYWORDS.stream().anyMatch(keyword -> item.title().contains(keyword) || item.expected().contains(keyword)));
    }

    private List<SourceFileDto> wholeFiles(
            RepoSnapshotDto snapshot,
            List<String> paths
    ) {
        return paths.stream()
                .map(path -> repoSourceReader.read(snapshot, path)
                        .map(source -> SourceFileDto.of(snapshot.repo(), path, Arrays.asList(source.split(LINE_BREAK, -1)), 1)))
                .flatMap(Optional::stream)
                .toList();
    }

    private CodeSelectionDto selection(
            List<RepoSnapshotDto> snapshots,
            List<SourceFileDto> files
    ) {
        Map<String, String> repoToSha = new LinkedHashMap<>();
        snapshots.forEach(snapshot -> repoToSha.put(snapshot.repo().getRepoName(), snapshot.commitSha()));
        return CodeSelectionDto.of(files, repoToSha);
    }
}
