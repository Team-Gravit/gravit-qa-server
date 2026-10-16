package gravit.qa.support;

import gravit.qa.adapter.llm.support.LlmCallGuard;
import gravit.qa.security.support.AdminVerificationCache;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

public class DatabaseClearExtension implements BeforeEachCallback {

    private static final String SELECT_TABLES = """
            SELECT tablename
            FROM pg_tables
            WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
            """;

    @Override
    public void beforeEach(ExtensionContext context) {
        ApplicationContext applicationContext = SpringExtension.getApplicationContext(context);
        JdbcTemplate jdbcTemplate = applicationContext.getBean(JdbcTemplate.class);

        List<String> tables = jdbcTemplate.queryForList(SELECT_TABLES, String.class);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }

        applicationContext.getBean(TestClock.class).setInstant(TestClockConfig.BASE_INSTANT);
        applicationContext.getBean(AdminMeStubServer.class).reset();
        applicationContext.getBean(AdminVerificationCache.class).clear();
        applicationContext.getBean(HttpStubServer.class).reset();
        applicationContext.getBean(RecordingSleeper.class).reset();
        applicationContext.getBean(LlmCallGuard.class).clear();
    }
}
