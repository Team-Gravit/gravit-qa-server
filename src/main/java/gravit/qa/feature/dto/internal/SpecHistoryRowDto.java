package gravit.qa.feature.dto.internal;

public record SpecHistoryRowDto(
        String sprint,
        String change,
        String reflectedOn
) {

    public static SpecHistoryRowDto of(
            String sprint,
            String change,
            String reflectedOn
    ) {
        return new SpecHistoryRowDto(sprint, change, reflectedOn);
    }
}
