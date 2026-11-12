package gravit.qa.snapshot.dto.internal;

public record StoredSnapshotDto(
        String storagePath,
        String contentHash,
        long sizeBytes
) {
}
