package gravit.qa.checklist.fixture;

import gravit.qa.checklist.dto.internal.TestCatalogEntryDto;
import gravit.qa.checklist.service.port.TestCatalog;

import java.util.Arrays;
import java.util.List;

public final class TestCatalogFixture {

    public static final String LRN_EMPTY_STATE_TEST = "LRN-02.empty-state";

    private TestCatalogFixture() {
    }

    public static TestCatalog 빈_카탈로그() {
        return 카탈로그();
    }

    public static TestCatalog LRN_02_테스트가_있는_카탈로그() {
        return 카탈로그(TestCatalogEntryDto.of(LRN_EMPTY_STATE_TEST, "LRN-02", "유닛 상세 빈 상태"));
    }

    public static TestCatalog 카탈로그(TestCatalogEntryDto... entries) {
        List<TestCatalogEntryDto> catalog = Arrays.asList(entries);
        return new TestCatalog() {
            @Override
            public boolean contains(String testRef) {
                return catalog.stream().anyMatch(entry -> entry.id().equals(testRef));
            }

            @Override
            public List<TestCatalogEntryDto> findByScreenId(String screenId) {
                return catalog.stream().filter(entry -> entry.screenId().equals(screenId)).toList();
            }
        };
    }
}
