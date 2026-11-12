package gravit.qa.checklist.dto.response;

import gravit.qa.checklist.dto.internal.CodeEvidenceDto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CodeEvidenceResponse(
        @Schema(
                description = "저장소 이름을 앞에 붙인 파일 경로",
                example = "gravit-server/src/main/java/gravit/code/notice/service/NoticeQueryService.java",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String path,

        @Schema(
                description = "근거 시작 줄 번호",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int startLine,

        @Schema(
                description = "근거 끝 줄 번호",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int endLine
) {

    public static CodeEvidenceResponse from(CodeEvidenceDto evidence) {
        return new CodeEvidenceResponse(evidence.path(), evidence.startLine(), evidence.endLine());
    }

    public static List<CodeEvidenceResponse> fromAll(List<CodeEvidenceDto> evidence) {
        return evidence == null ? List.of() : evidence.stream().map(CodeEvidenceResponse::from).toList();
    }
}
