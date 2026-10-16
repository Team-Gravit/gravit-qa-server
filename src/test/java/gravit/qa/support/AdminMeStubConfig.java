package gravit.qa.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class AdminMeStubConfig {

    @Bean(destroyMethod = "stop")
    public AdminMeStubServer adminMeStubServer() {
        return new AdminMeStubServer();
    }

    @Bean
    public DynamicPropertyRegistrar authHostProperties(AdminMeStubServer adminMeStubServer) {
        return registry -> registry.add("qa.auth.host", adminMeStubServer::baseUrl);
    }
}
