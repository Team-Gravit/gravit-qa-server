package gravit.qa.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, TestClockConfig.class, AdminMeStubConfig.class, ExternalApiStubConfig.class})
@ExtendWith(DatabaseClearExtension.class)
public @interface QaSpringBootTest {
}
