package gravit.qa.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    private static final String EXTERNAL_URL_ENV = "QA_TEST_DB_URL";
    private static final String EXTERNAL_USERNAME_ENV = "QA_TEST_DB_USERNAME";
    private static final String EXTERNAL_PASSWORD_ENV = "QA_TEST_DB_PASSWORD";
    private static final String POSTGRES_IMAGE = "postgres:16-alpine";

    @Bean
    public DynamicPropertyRegistrar postgresProperties() {
        return registry -> {
            String externalUrl = System.getenv(EXTERNAL_URL_ENV);
            if (externalUrl != null && !externalUrl.isBlank()) {
                registry.add("spring.datasource.url", () -> externalUrl);
                registry.add("spring.datasource.username", () -> envOrDefault(EXTERNAL_USERNAME_ENV, "postgres"));
                registry.add("spring.datasource.password", () -> envOrDefault(EXTERNAL_PASSWORD_ENV, ""));
                return;
            }

            PostgreSQLContainer<?> container = ContainerHolder.POSTGRES;
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        };
    }

    private static String envOrDefault(
            String name,
            String defaultValue
    ) {
        String value = System.getenv(name);
        return value == null ? defaultValue : value;
    }

    private static final class ContainerHolder {

        private static final PostgreSQLContainer<?> POSTGRES = startPostgres();

        private static PostgreSQLContainer<?> startPostgres() {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>(POSTGRES_IMAGE);
            container.start();
            return container;
        }
    }
}
