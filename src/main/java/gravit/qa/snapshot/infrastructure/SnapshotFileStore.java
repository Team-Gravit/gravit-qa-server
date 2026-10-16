package gravit.qa.snapshot.infrastructure;

import gravit.qa.global.config.StorageProps;
import gravit.qa.snapshot.domain.SnapshotSource;
import gravit.qa.snapshot.dto.internal.StoredSnapshotDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Component
@RequiredArgsConstructor
public class SnapshotFileStore {

    public static final String SNAPSHOT_DIRECTORY = "snapshots";

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String TEMP_FILE_PREFIX = ".tmp-";
    private static final String PATH_SEPARATOR = "/";
    private static final Pattern FILE_NAME = Pattern.compile("^(?!\\.{1,2}$)[^/\\\\\\x00]{1,200}$");

    private final StorageProps storageProps;

    public StoredSnapshotDto save(
            long sprintId,
            SnapshotSource source,
            String fileName,
            String content
    ) {
        return save(sprintId, source, fileName, content.getBytes(StandardCharsets.UTF_8));
    }

    public StoredSnapshotDto save(
            long sprintId,
            SnapshotSource source,
            String fileName,
            byte[] content
    ) {
        validate(sprintId, fileName);
        String storagePath = String.join(PATH_SEPARATOR, SNAPSHOT_DIRECTORY, String.valueOf(sprintId), source.name(), fileName);
        Path root = Path.of(storageProps.root()).toAbsolutePath().normalize();
        Path target = root.resolve(storagePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("스냅샷 경로가 저장소 밖을 가리킵니다: " + storagePath);
        }

        write(target, content);
        return new StoredSnapshotDto(storagePath, sha256(content), content.length);
    }

    private void write(
            Path target,
            byte[] content
    ) {
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), TEMP_FILE_PREFIX, null);
            Files.write(temp, content);
            Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new UncheckedIOException("스냅샷 파일을 저장하지 못했습니다: " + target.getFileName(), e);
        }
    }

    private void validate(
            long sprintId,
            String fileName
    ) {
        if (sprintId <= 0) {
            throw new IllegalArgumentException("스프린트 ID가 올바르지 않습니다: " + sprintId);
        }
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("스냅샷 파일 이름이 올바르지 않습니다: " + fileName);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(HASH_ALGORITHM).digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
