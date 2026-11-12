package gravit.qa.snapshot.dto.internal;

import gravit.qa.snapshot.domain.SnapshotSource;

import java.time.LocalDateTime;

public record SnapshotCreateDto(
        SnapshotSource source,
        String sourceRef,
        String contentHash,
        String storagePath,
        LocalDateTime capturedAt
) {

    public static SnapshotCreateDto of(
            SnapshotSource source,
            String sourceRef,
            String contentHash,
            String storagePath,
            LocalDateTime capturedAt
    ) {
        return new SnapshotCreateDto(source, sourceRef, contentHash, storagePath, capturedAt);
    }
}
