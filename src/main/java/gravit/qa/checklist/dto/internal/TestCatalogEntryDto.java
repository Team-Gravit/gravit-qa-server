package gravit.qa.checklist.dto.internal;

public record TestCatalogEntryDto(
        String id,
        String screenId,
        String title
) {

    public static TestCatalogEntryDto of(
            String id,
            String screenId,
            String title
    ) {
        return new TestCatalogEntryDto(id, screenId, title);
    }
}
