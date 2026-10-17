package gravit.qa.checklist.support;

import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.ClassificationCriteriaDto;
import gravit.qa.checklist.dto.internal.ClassificationResultDto;
import gravit.qa.checklist.service.port.TestCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChecklistClassifier {

    private final TestCatalog testCatalog;

    public ClassificationResultDto classify(
            ClassificationCriteriaDto criteria,
            String testRef
    ) {
        if (criteria == null || !criteria.allMet()) {
            return ClassificationResultDto.of(Classification.MANUAL, false);
        }
        if (testCatalog.contains(testRef)) {
            return ClassificationResultDto.of(Classification.AUTO, false);
        }
        return ClassificationResultDto.of(Classification.MANUAL, true);
    }
}
