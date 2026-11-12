package gravit.qa.checklist.dto.internal;

public record CodeEvidenceDto(
        String path,
        int startLine,
        int endLine
) {

    public static CodeEvidenceDto of(
            String path,
            int startLine,
            int endLine
    ) {
        return new CodeEvidenceDto(path, startLine, endLine);
    }
}
