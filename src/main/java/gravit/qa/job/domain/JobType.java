package gravit.qa.job.domain;

import lombok.Getter;

import java.util.List;

@Getter
public enum JobType {

    SPEC_LOAD(List.of("CONNECT", "SPEC_COLLECT")),
    QA_PREPARE(List.of("CHECKLIST_GENERATE", "DESIGN_REVIEW", "AUTO_REVIEW"));

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
