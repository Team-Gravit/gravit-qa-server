package gravit.qa.autoReview.support;

import gravit.qa.adapter.github.domain.GithubRepo;
import gravit.qa.adapter.github.dto.internal.GithubTreeDto;
import gravit.qa.adapter.github.dto.internal.GithubTreeEntryDto;
import gravit.qa.adapter.github.infrastructure.GithubClient;
import gravit.qa.autoReview.dto.internal.RepoSnapshotDto;
import gravit.qa.global.config.StorageProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepoSourceReader {

    public static final String CACHE_DIRECTORY = "repo-cache";

    private static final Pattern BLOB_SHA = Pattern.compile("^[0-9a-f]{40}$");
    private static final String TEMP_FILE_PREFIX = ".tmp-";
    private static final String TREE_KEY = "%s@%s";
    private static final long MAX_FILE_BYTES = 200_000;

    private final Map<String, RepoSnapshotDto> keyToTree = new ConcurrentHashMap<>();

    private final GithubClient githubClient;

    private final StorageProps storageProps;

    public RepoSnapshotDto tree(
            GithubRepo repo,
            String commitSha
    ) {
        return keyToTree.computeIfAbsent(TREE_KEY.formatted(repo.getRepoName(), commitSha), key -> load(repo, commitSha));
    }

    public Optional<String> read(
            RepoSnapshotDto snapshot,
            String path
    ) {
        Optional<GithubTreeEntryDto> entry = snapshot.find(path);
        if (entry.isEmpty() || entry.get().size() > MAX_FILE_BYTES) {
            return Optional.empty();
        }
        String blobSha = entry.get().blobSha();
        if (!BLOB_SHA.matcher(blobSha).matches()) {
            return Optional.empty();
        }

        Path cached = cacheRoot().resolve(blobSha);
        if (Files.exists(cached)) {
            try {
                return Optional.of(Files.readString(cached, StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.warn("저장소 파일 캐시를 읽지 못해 다시 받습니다: {}", path);
            }
        }
        String content = githubClient.getBlob(snapshot.repo(), blobSha);
        write(cached, content);
        return Optional.of(content);
    }

    public void clear() {
        keyToTree.clear();
        Path root = cacheRoot();
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> files = Files.list(root)) {
            files.forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    log.warn("저장소 파일 캐시를 지우지 못했습니다: {}", file.getFileName());
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("저장소 파일 캐시 목록을 읽지 못했습니다", e);
        }
    }

    private RepoSnapshotDto load(
            GithubRepo repo,
            String commitSha
    ) {
        GithubTreeDto tree = githubClient.getTree(repo, commitSha);
        if (tree.truncated()) {
            log.warn("{} 파일 목록이 잘렸습니다. 일부 파일은 1차 판정에서 빠집니다. commit={}", repo.getRepoName(), commitSha);
        }
        Map<String, GithubTreeEntryDto> pathToEntry = new LinkedHashMap<>();
        List<GithubTreeEntryDto> entries = tree.entries();
        entries.forEach(entry -> pathToEntry.put(entry.path(), entry));
        return new RepoSnapshotDto(repo, commitSha, Map.copyOf(pathToEntry));
    }

    private Path cacheRoot() {
        return Path.of(storageProps.root()).toAbsolutePath().normalize().resolve(CACHE_DIRECTORY);
    }

    private void write(
            Path target,
            String content
    ) {
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), TEMP_FILE_PREFIX, null);
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (IOException e) {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
            throw new UncheckedIOException("저장소 파일 캐시를 저장하지 못했습니다: " + target.getFileName(), e);
        }
    }
}
