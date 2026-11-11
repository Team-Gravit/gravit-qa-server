package gravit.qa.designReview.domain;

import gravit.qa.adapter.figma.domain.FigmaPlatform;
import gravit.qa.feature.domain.Part;
import gravit.qa.feature.dto.internal.SpecRowDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DesignPlatform {

    WEB(Part.WEB, FigmaPlatform.WEB),
    APP(Part.APP, FigmaPlatform.AOS);

    private final Part part;
    private final FigmaPlatform figmaPlatform;

    public boolean appliesTo(SpecRowDto row) {
        return this == WEB ? row.appliesToWeb() : row.appliesToApp();
    }
}
