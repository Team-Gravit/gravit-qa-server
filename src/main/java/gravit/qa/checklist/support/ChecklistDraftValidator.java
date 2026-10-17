package gravit.qa.checklist.support;

import gravit.qa.checklist.domain.Classification;
import gravit.qa.checklist.dto.internal.ChecklistDraftItemOutputDto;
import gravit.qa.checklist.dto.internal.ChecklistDraftOutputDto;
import gravit.qa.checklist.dto.internal.ChecklistItemDraftDto;
import gravit.qa.checklist.service.port.TestCatalog;
import gravit.qa.feature.domain.Part;
import gravit.qa.persona.domain.PersonaKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ChecklistDraftValidator {

    private static final String ITEMS_FIELD = "items";
    private static final String ITEM_PATH = "items[%d]";

    private final TestCatalog testCatalog;

    public List<String> validate(
            ChecklistDraftOutputDto output,
            List<Part> featureParts
    ) {
        List<String> errors = new ArrayList<>();
        if (output.items() == null || output.items().isEmpty()) {
            errors.add(ITEMS_FIELD + "에 체크 항목이 1개 이상 있어야 합니다.");
            return errors;
        }

        for (int index = 0; index < output.items().size(); index++) {
            ChecklistDraftItemOutputDto item = output.items().get(index);
            String path = ITEM_PATH.formatted(index);
            requireText(item.evidence(), path + ".evidence", errors);
            requireText(item.title(), path + ".title", errors);
            requireText(item.expected(), path + ".expected", errors);
            validateParts(item.parts(), featureParts, path, errors);
            validatePersonaKey(item.personaKey(), path, errors);
            validateTestRef(item.testRef(), path, errors);
        }
        return errors;
    }

    public List<String> validateDrafts(
            List<ChecklistItemDraftDto> drafts,
            List<Part> featureParts
    ) {
        List<String> errors = new ArrayList<>();
        if (drafts.isEmpty()) {
            errors.add(ITEMS_FIELD + "에 체크 항목이 1개 이상 있어야 합니다.");
            return errors;
        }

        for (int index = 0; index < drafts.size(); index++) {
            ChecklistItemDraftDto draft = drafts.get(index);
            String path = ITEM_PATH.formatted(index);
            requireText(draft.title(), path + ".title", errors);
            requireText(draft.expected(), path + ".expected", errors);
            validateParts(draft.parts(), featureParts, path, errors);
            validatePersonaKey(draft.personaKey(), path, errors);
            validateTestRef(draft.testRef(), path, errors);
            if (draft.classification() == Classification.AUTO && !testCatalog.contains(draft.testRef())) {
                errors.add(path + ": AUTO 항목의 testRef가 테스트 카탈로그에 없습니다.");
            }
        }
        return errors;
    }

    private void requireText(
            String value,
            String field,
            List<String> errors
    ) {
        if (value == null || value.isBlank()) {
            errors.add(field + "가 비어 있습니다.");
        }
    }

    private void validateParts(
            List<Part> parts,
            List<Part> featureParts,
            String path,
            List<String> errors
    ) {
        if (parts == null || parts.isEmpty()) {
            errors.add(path + ".parts에 파트가 1개 이상 있어야 합니다.");
            return;
        }
        if (new HashSet<>(parts).size() != parts.size()) {
            errors.add(path + ".parts에 같은 파트가 중복됩니다.");
        }
        parts.stream()
                .filter(part -> !featureParts.contains(part))
                .distinct()
                .forEach(part -> errors.add(path + ".parts의 " + part + "는 기능 파트 " + featureParts + "에 없습니다."));
    }

    private void validatePersonaKey(
            String personaKey,
            String path,
            List<String> errors
    ) {
        if (personaKey != null && !PersonaKey.isKnown(personaKey)) {
            errors.add(path + ".personaKey '" + personaKey + "'는 페르소나 목록에 없습니다.");
        }
    }

    private void validateTestRef(
            String testRef,
            String path,
            List<String> errors
    ) {
        if (testRef != null && !testCatalog.contains(testRef)) {
            errors.add(path + ".testRef '" + testRef + "'는 테스트 카탈로그에 없습니다.");
        }
    }
}
