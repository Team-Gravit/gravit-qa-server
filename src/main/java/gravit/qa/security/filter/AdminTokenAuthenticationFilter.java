package gravit.qa.security.filter;

import gravit.qa.security.exception.AdminAuthException;
import gravit.qa.security.principal.LoginAdmin;
import gravit.qa.security.support.AdminVerifier;
import gravit.qa.security.support.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import static gravit.qa.global.exception.domain.CustomErrorCode.TOKEN_MISSING;

@RequiredArgsConstructor
public class AdminTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_PATH_PREFIX = "/api/";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AdminVerifier adminVerifier;
    private final ErrorResponseWriter errorResponseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String accessToken = extractToken(request);
        if (accessToken == null) {
            errorResponseWriter.write(response, TOKEN_MISSING);
            return;
        }

        LoginAdmin admin;
        try {
            admin = adminVerifier.verify(accessToken);
        } catch (AdminAuthException e) {
            errorResponseWriter.write(response, e.getErrorCode());
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                admin,
                null,
                List.of(new SimpleGrantedAuthority(ROLE_ADMIN))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
