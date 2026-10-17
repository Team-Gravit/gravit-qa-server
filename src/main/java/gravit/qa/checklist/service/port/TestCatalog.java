package gravit.qa.checklist.service.port;

import gravit.qa.checklist.dto.internal.TestCatalogEntryDto;

import java.util.List;

public interface TestCatalog {

    boolean contains(String testRef);

    List<TestCatalogEntryDto> findByScreenId(String screenId);
}
