package gravit.qa.checklist.fixture;

import gravit.qa.checklist.service.port.TestCatalog;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestCatalogStubConfig {

    @Bean
    @Primary
    public TestCatalog stubTestCatalog() {
        return TestCatalogFixture.LRN_02_테스트가_있는_카탈로그();
    }
}
