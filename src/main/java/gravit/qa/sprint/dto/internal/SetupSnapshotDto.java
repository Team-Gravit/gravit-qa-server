package gravit.qa.sprint.dto.internal;

import gravit.qa.snapshot.domain.SnapshotSource;
import gravit.qa.snapshot.dto.internal.SnapshotCreateDto;
import gravit.qa.snapshot.dto.internal.StoredSnapshotDto;

import java.time.LocalDateTime;

public record SetupSnapshotDto(
        SnapshotSource source,
        String sourceRef,
        String screenId,
        StoredSnapshotDto stored,
        LocalDateTime capturedAt
) {

    public static SetupSnapshotDto of(
            SnapshotSource source,
            String sourceRef,
            String screenId,
            StoredSnapshotDto stored,
            LocalDateTime capturedAt
    ) {
        return new SetupSnapshotDto(source, sourceRef, screenId, stored, capturedAt);
    }

    public SnapshotCreateDto toCreateDto() {
        return SnapshotCreateDto.of(source, sourceRef, stored.contentHash(), stored.storagePath(), capturedAt);
    }

    public boolean isShared() {
        return screenId == null;
    }
}
