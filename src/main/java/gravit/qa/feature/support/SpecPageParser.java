package gravit.qa.feature.support;

import gravit.qa.adapter.exception.InvalidNotionSpecException;
import gravit.qa.adapter.notion.dto.internal.NotionHistoryTableDto;
import gravit.qa.adapter.notion.dto.internal.NotionPageBodyDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecRowDto;
import gravit.qa.adapter.notion.dto.internal.NotionSpecTableDto;
import gravit.qa.adapter.notion.support.NotionBlockRenderer;
import gravit.qa.adapter.notion.support.NotionHistoryTableParser;
import gravit.qa.adapter.notion.support.NotionSpecBodyParser;
import gravit.qa.adapter.notion.support.NotionSpecTableParser;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.domain.SpecSection;
import gravit.qa.feature.domain.SpecStatus;
import gravit.qa.feature.dto.internal.SpecPageDto;
import gravit.qa.feature.dto.internal.SpecRowDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpecPageParser {

    public static SpecPageDto parse(
            NotionSpecDto spec,
            NotionPageBodyDto body
    ) {
        NotionSpecTableDto table = NotionSpecTableParser.parse(body.blocks());
        List<Part> parts = toParts(table);
        SpecStatus specStatus = toSpecStatus(table);
        NotionHistoryTableDto history = NotionHistoryTableParser.parse(body.blocks());
        List<SpecRowDto> rows = NotionSpecBodyParser.parseRows(body.blocks()).stream()
                .map(SpecPageParser::toSpecRow)
                .toList();
        String markdown = NotionBlockRenderer.toMarkdown(NotionSpecBodyParser.specBlocks(body.blocks(), history));

        return new SpecPageDto(spec, body, table, parts, specStatus, rows, history, markdown);
    }

    private static SpecRowDto toSpecRow(NotionSpecRowDto row) {
        SpecSection section = SpecSection.fromTitle(row.sectionTitle())
                .orElseThrow(() -> new IllegalStateException("알 수 없는 명세 절입니다: " + row.sectionTitle()));
        return SpecRowDto.of(row.rowId(), section, row.columns(), row.cells());
    }

    private static List<Part> toParts(NotionSpecTableDto table) {
        if (table.parts().isEmpty()) {
            throw new InvalidNotionSpecException("명세 맨 위 표의 '" + NotionSpecTableParser.PARTS_ITEM + "' 칸이 비어 있습니다");
        }
        return table.parts().stream()
                .map(label -> Part.fromNotionLabel(label)
                        .orElseThrow(() -> new InvalidNotionSpecException("명세 맨 위 표의 파트 값 '" + label + "'을 알 수 없습니다")))
                .distinct()
                .toList();
    }

    private static SpecStatus toSpecStatus(NotionSpecTableDto table) {
        return SpecStatus.fromNotionLabel(table.specStatus())
                .orElseThrow(() -> new InvalidNotionSpecException("명세 맨 위 표의 명세 상태 값 '" + table.specStatus() + "'을 알 수 없습니다"));
    }
}
