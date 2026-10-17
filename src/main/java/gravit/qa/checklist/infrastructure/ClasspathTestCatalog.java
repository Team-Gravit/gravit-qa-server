package gravit.qa.checklist.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gravit.qa.checklist.dto.internal.TestCatalogEntryDto;
import gravit.qa.checklist.service.port.TestCatalog;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClasspathTestCatalog implements TestCatalog {

    private static final String CATALOG_LOCATION = "test-catalog.json";

    private Map<String, TestCatalogEntryDto> idToEntry = Map.of();

    private final ObjectMapper objectMapper;

    @PostConstruct
    void load() {
        List<TestCatalogEntryDto> entries = read();

        Map<String, TestCatalogEntryDto> loaded = new LinkedHashMap<>();
        for (TestCatalogEntryDto entry : entries) {
            if (loaded.put(entry.id(), entry) != null) {
                throw new IllegalStateException("테스트 카탈로그 ID가 중복됩니다: " + entry.id());
            }
        }
        this.idToEntry = Map.copyOf(loaded);
        log.info("테스트 카탈로그 {}건을 읽었습니다.", idToEntry.size());
    }

    @Override
    public boolean contains(String testRef) {
        return testRef != null && idToEntry.containsKey(testRef);
    }

    @Override
    public List<TestCatalogEntryDto> findByScreenId(String screenId) {
        return idToEntry.values().stream()
                .filter(entry -> entry.screenId().equals(screenId))
                .toList();
    }

    private List<TestCatalogEntryDto> read() {
        try (InputStream input = new ClassPathResource(CATALOG_LOCATION).getInputStream()) {
            return objectMapper.readValue(input, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("테스트 카탈로그를 읽지 못했습니다: " + CATALOG_LOCATION, e);
        }
    }
}
