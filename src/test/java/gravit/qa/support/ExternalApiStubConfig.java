package gravit.qa.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@TestConfiguration(proxyBeanMethods = false)
public class ExternalApiStubConfig {

    public static final String OPENAPI_PATH = "/v3/api-docs";

    @Bean(destroyMethod = "stop")
    public HttpStubServer externalApiStubServer() {
        return new HttpStubServer();
    }

    @Bean
    @Primary
    public RecordingSleeper recordingSleeper(TestClock testClock) {
        return new RecordingSleeper(testClock);
    }

    @Bean
    public DynamicPropertyRegistrar externalApiProperties(HttpStubServer externalApiStubServer) {
        Path storageRoot = createStorageRoot();
        return registry -> {
            registry.add("qa.adapter.github.base-url", externalApiStubServer::baseUrl);
            registry.add("qa.adapter.notion.base-url", externalApiStubServer::baseUrl);
            registry.add("qa.adapter.figma.base-url", externalApiStubServer::baseUrl);
            registry.add("qa.adapter.openapi.url", () -> externalApiStubServer.baseUrl() + OPENAPI_PATH);
            registry.add("qa.adapter.llm.base-url", externalApiStubServer::baseUrl);
            registry.add("qa.storage.root", storageRoot::toString);
        };
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("gravit-qa-storage-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
