package gravit.qa.sprint.dto.internal;

import gravit.qa.feature.dto.internal.SpecPageDto;

import java.util.List;

public record SpecCollectDto(
        List<SpecPageDto> pages,
        List<SetupSnapshotDto> snapshots
) {
}
