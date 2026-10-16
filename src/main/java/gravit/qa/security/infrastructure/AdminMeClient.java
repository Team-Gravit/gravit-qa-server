package gravit.qa.security.infrastructure;

import gravit.qa.security.config.AuthProps;
import gravit.qa.security.dto.internal.AdminMeDto;
import gravit.qa.security.exception.AdminAuthException;
import gravit.qa.security.principal.LoginAdmin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

import static gravit.qa.global.exception.domain.CustomErrorCode.AUTH_SERVER_UNAVAILABLE;
import static gravit.qa.global.exception.domain.CustomErrorCode.NOT_ADMIN;
import static gravit.qa.global.exception.domain.CustomErrorCode.TOKEN_INVALID;

@Slf4j
@Component
public class AdminMeClient {

    public static final String ADMIN_ME_PATH = "/api/v1/admin/me";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int STATUS_OK = 200;
    private static final int STATUS_UNAUTHORIZED = 401;
    private static final int STATUS_FORBIDDEN = 403;

    private final RestClient restClient;

    public AdminMeClient(
            RestClient.Builder restClientBuilder,
            AuthProps authProps
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(authProps.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(authProps.timeout());

        this.restClient = restClientBuilder
                .baseUrl(authProps.host())
                .requestFactory(requestFactory)
                .build();
    }

    public LoginAdmin fetch(String accessToken) {
        try {
            return restClient.get()
                    .uri(ADMIN_ME_PATH)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == STATUS_OK) {
                            return toLoginAdmin(response.bodyTo(AdminMeDto.class));
                        }
                        if (status == STATUS_UNAUTHORIZED) {
                            throw new AdminAuthException(TOKEN_INVALID);
                        }
                        if (status == STATUS_FORBIDDEN) {
                            throw new AdminAuthException(NOT_ADMIN);
                        }
                        log.warn("관리자 판정 응답을 해석할 수 없습니다. status={}", status);
                        throw new AdminAuthException(AUTH_SERVER_UNAVAILABLE);
                    });
        } catch (AdminAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("인증 서버 호출 실패: {}", e.getClass().getSimpleName());
            throw new AdminAuthException(AUTH_SERVER_UNAVAILABLE);
        }
    }

    private LoginAdmin toLoginAdmin(AdminMeDto adminMe) {
        if (adminMe == null || adminMe.adminId() == null) {
            log.warn("관리자 판정 200 응답에 adminId가 없습니다.");
            throw new AdminAuthException(AUTH_SERVER_UNAVAILABLE);
        }
        return LoginAdmin.from(adminMe);
    }
}
