package gravit.qa.security.config;

import gravit.qa.security.filter.AdminTokenAuthenticationFilter;
import gravit.qa.security.support.AdminVerifier;
import gravit.qa.security.support.ErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static gravit.qa.global.exception.domain.CustomErrorCode.NOT_ADMIN;
import static gravit.qa.global.exception.domain.CustomErrorCode.TOKEN_MISSING;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AdminVerifier adminVerifier;
    private final ErrorResponseWriter errorResponseWriter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // 헬스 체크
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Swagger
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // QA API
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(
                        new AdminTokenAuthenticationFilter(adminVerifier, errorResponseWriter),
                        UsernamePasswordAuthenticationFilter.class
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, e) -> errorResponseWriter.write(response, TOKEN_MISSING))
                        .accessDeniedHandler((request, response, e) -> errorResponseWriter.write(response, NOT_ADMIN)));

        return http.build();
    }
}
