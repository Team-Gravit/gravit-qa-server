package gravit.qa.adapter.figma.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FigmaOutlineRendererTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void 프레임_기준_좌표로_위에서_아래_왼쪽에서_오른쪽_순서로_글자와_컴포넌트를_적고_숨긴_레이어와_기본_이름_묶음은_뺀다() throws Exception {
        // given
        JsonNode frame = OBJECT_MAPPER.readTree("""
                {"id": "4:2", "name": "ACC-01", "type": "FRAME", "absoluteBoundingBox": {"x": 1000, "y": 500, "width": 1920, "height": 1080}, "children": [
                  {"id": "4:20", "name": "Frame 12", "type": "FRAME", "absoluteBoundingBox": {"x": 1000, "y": 900, "width": 400, "height": 60}, "children": [
                    {"id": "4:21", "name": "카카오 버튼", "type": "INSTANCE", "absoluteBoundingBox": {"x": 1220, "y": 900, "width": 180, "height": 60}, "children": []},
                    {"id": "4:22", "name": "Google 버튼", "type": "INSTANCE", "absoluteBoundingBox": {"x": 1000, "y": 900, "width": 180, "height": 60}, "children": []}
                  ]},
                  {"id": "4:10", "name": "제목", "type": "TEXT", "characters": "그래빗과 함께\\nCS 지식을", "style": {"fontSize": 24, "fontWeight": 700},
                   "fills": [{"type": "SOLID", "color": {"r": 1, "g": 1, "b": 1, "a": 1}}], "absoluteBoundingBox": {"x": 1100, "y": 600, "width": 300, "height": 60}},
                  {"id": "4:11", "name": "숨김", "type": "TEXT", "visible": false, "characters": "보이지 않음", "absoluteBoundingBox": {"x": 1000, "y": 500, "width": 10, "height": 10}}
                ]}
                """);

        // when
        String outline = FigmaOutlineRenderer.render(frame);

        // then
        assertThat(outline).isEqualTo("""
                ### 프레임 ACC-01 [4:2] 1920x1080
                - [4:10] 글자 "그래빗과 함께⏎CS 지식을" (x=100, y=100, 300x60) 24px 굵기 700 #FFFFFF
                - [4:22] 컴포넌트 Google 버튼 (x=0, y=400, 180x60)
                - [4:21] 컴포넌트 카카오 버튼 (x=220, y=400, 180x60)""");
    }
}
