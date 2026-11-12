package gravit.qa.autoReview.support;

import gravit.qa.autoReview.dto.internal.AutoReviewOutputDto;
import gravit.qa.autoReview.dto.internal.AutoReviewResultOutputDto;
import gravit.qa.autoReview.dto.internal.CodeEvidenceOutputDto;
import gravit.qa.autoReview.dto.internal.SourceFileDto;
import gravit.qa.checklist.domain.Verdict;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AutoReviewValidator {

    private static final String RESULT_PATH = "results[%d]";
    private static final String DELIMITER = ", ";

    public static List<String> validate(
            AutoReviewOutputDto output,
            Collection<Long> itemIds,
            List<SourceFileDto> slices
    ) {
        List<String> errors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<AutoReviewResultOutputDto> results = output.results() == null ? List.of() : output.results();

        for (int index = 0; index < results.size(); index++) {
            AutoReviewResultOutputDto result = results.get(index);
            String path = RESULT_PATH.formatted(index);
            if (!itemIds.contains(result.itemId())) {
                errors.add(path + ".itemId " + result.itemId() + "는 입력에 없는 항목 번호입니다.");
                continue;
            }
            if (!seen.add(result.itemId())) {
                errors.add(path + ".itemId " + result.itemId() + "를 두 번 판정했습니다. 항목마다 한 번만 판정합니다.");
            }
            if (result.reason() == null || result.reason().isBlank()) {
                errors.add(path + ".reason이 비어 있습니다.");
            }
            List<CodeEvidenceOutputDto> evidence = result.evidence() == null ? List.of() : result.evidence();
            if (result.verdict() != Verdict.UNKNOWN && evidence.isEmpty()) {
                errors.add(path + ": " + result.verdict() + " 판정에는 코드 근거가 1개 이상 있어야 합니다.");
            }
            for (CodeEvidenceOutputDto location : evidence) {
                if (!isInSlices(location, slices)) {
                    errors.add(path + ".evidence " + location.path() + ":" + location.startLine() + "-" + location.endLine() + "는 입력 코드 범위에 없습니다.");
                }
            }
        }

        Set<Long> missing = new LinkedHashSet<>(itemIds);
        missing.removeAll(seen);
        if (!missing.isEmpty()) {
            errors.add("판정하지 않은 항목이 있습니다: " + String.join(DELIMITER, missing.stream().map(String::valueOf).toList()));
        }
        return errors;
    }

    private static boolean isInSlices(
            CodeEvidenceOutputDto location,
            List<SourceFileDto> slices
    ) {
        if (location.path() == null || location.startLine() > location.endLine()) {
            return false;
        }
        String target = location.path().trim();
        return slices.stream().anyMatch(slice -> (slice.label().equals(target) || slice.path().equals(target))
                && location.startLine() >= slice.firstLine()
                && location.endLine() <= slice.lastLine());
    }
}
