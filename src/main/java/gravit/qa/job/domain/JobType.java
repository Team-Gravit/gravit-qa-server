package gravit.qa.job.domain;

import lombok.Getter;

import java.util.List;

@Getter
public enum JobType {

    SETUP(List.of("BASELINE", "NOTION", "FIGMA", "DIFF", "OPENAPI", "INTERPRET", "VALIDATE", "APPLY")),
    AUTO_RUN(List.of("FIXTURE", "TOKEN", "EXECUTE", "JUDGE")),
    REVIEW_COLLECT(List.of("COLLECT")),
    EVIDENCE_CLEANUP(List.of("CLEANUP"));

    private final List<String> stepNames;

    JobType(List<String> stepNames) {
        this.stepNames = stepNames;
    }

    public int seqOf(String stepName) {
        int index = stepNames.indexOf(stepName);
        if (index < 0) {
            throw new IllegalArgumentException(name() + " 작업에 없는 단계입니다: " + stepName);
        }
        return index + 1;
    }
}
