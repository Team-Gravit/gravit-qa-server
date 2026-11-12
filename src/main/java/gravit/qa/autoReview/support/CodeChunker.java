package gravit.qa.autoReview.support;

import gravit.qa.autoReview.dto.internal.CodeChunkDto;
import gravit.qa.autoReview.dto.internal.CodeChunksDto;
import gravit.qa.autoReview.dto.internal.SourceFileDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CodeChunker {

    private static final String FILE_HEADER = "### %s (%d-%d행)";
    private static final String LINE_FORMAT = "%5d| %s";
    private static final String LINE_BREAK = "\n";

    public static CodeChunksDto chunk(
            List<SourceFileDto> files,
            int budget,
            int maxChunks
    ) {
        List<CodeChunkDto> chunks = new ArrayList<>();
        List<SourceFileDto> current = new ArrayList<>();
        StringBuilder rendered = new StringBuilder();
        Set<String> dropped = new LinkedHashSet<>();

        for (SourceFileDto file : files) {
            for (SourceFileDto slice : split(file, budget)) {
                String block = render(slice);
                if (!current.isEmpty() && rendered.length() + block.length() > budget) {
                    chunks.add(new CodeChunkDto(List.copyOf(current), rendered.toString()));
                    current.clear();
                    rendered.setLength(0);
                }
                if (chunks.size() >= maxChunks) {
                    dropped.add(slice.label());
                    continue;
                }
                current.add(slice);
                rendered.append(block);
            }
        }
        if (!current.isEmpty() && chunks.size() < maxChunks) {
            chunks.add(new CodeChunkDto(List.copyOf(current), rendered.toString()));
        }
        return new CodeChunksDto(chunks, List.copyOf(dropped));
    }

    public static String render(SourceFileDto slice) {
        StringBuilder builder = new StringBuilder(FILE_HEADER.formatted(slice.label(), slice.firstLine(), slice.lastLine())).append(LINE_BREAK);
        for (int index = 0; index < slice.lines().size(); index++) {
            builder.append(String.format(Locale.ROOT, LINE_FORMAT, slice.firstLine() + index, slice.lines().get(index))).append(LINE_BREAK);
        }
        return builder.append(LINE_BREAK).toString();
    }

    private static List<SourceFileDto> split(
            SourceFileDto file,
            int budget
    ) {
        if (render(file).length() <= budget) {
            return List.of(file);
        }
        List<SourceFileDto> slices = new ArrayList<>();
        int start = 0;
        while (start < file.lines().size()) {
            int end = start;
            int length = FILE_HEADER.length() + file.label().length();
            while (end < file.lines().size() && length + file.lines().get(end).length() + LINE_FORMAT.length() + 4 <= budget) {
                length += file.lines().get(end).length() + LINE_FORMAT.length() + 4;
                end++;
            }
            if (end == start) {
                end = start + 1;
            }
            slices.add(SourceFileDto.of(file.repo(), file.path(), file.lines().subList(start, end), file.firstLine() + start));
            start = end;
        }
        return slices;
    }
}
