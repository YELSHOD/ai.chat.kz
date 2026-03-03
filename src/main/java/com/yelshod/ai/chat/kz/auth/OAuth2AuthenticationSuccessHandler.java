package com.yelshod.ai.chat.kz.auth;

import com.yelshod.ai.chat.kz.auth.dto.AuthResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final String redirectSuccessUri;

    public OAuth2AuthenticationSuccessHandler(AuthService authService,
                                              @Value("${app.auth.oauth2.redirect-success-uri:http://localhost:5173/auth/callback}") String redirectSuccessUri) {
        this.authService = authService;
        this.redirectSuccessUri = redirectSuccessUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token)) {
                sendErrorRedirect(response, "Invalid OAuth2 authentication");
                return;
            }

            OAuth2User oauthUser = oauth2Token.getPrincipal();
            Map<String, Object> attributes = oauthUser.getAttributes();

            String provider = oauth2Token.getAuthorizedClientRegistrationId();
            String providerUserId = resolveProviderUserId(oauthUser, attributes);
            String email = resolveEmail(attributes);
            String username = resolveUsername(attributes, email);

            AuthResponse authResponse = authService.oauthLogin(provider, providerUserId, email, username);

            String redirectTarget = redirectSuccessUri + "#" +
                    "accessToken=" + encode(authResponse.accessToken()) +
                    "&accessTokenExpiresAt=" + encode(authResponse.accessTokenExpiresAt().toString()) +
                    "&refreshToken=" + encode(authResponse.refreshToken()) +
                    "&refreshTokenExpiresAt=" + encode(authResponse.refreshTokenExpiresAt().toString()) +
                    "&userId=" + encode(String.valueOf(authResponse.userId())) +
                    "&email=" + encode(authResponse.email()) +
                    "&username=" + encode(authResponse.username());

            response.sendRedirect(redirectTarget);
        } catch (Exception exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "OAuth2 authentication failed"
                    : exception.getMessage();
            sendErrorRedirect(response, message);
        }
    }

    private String resolveProviderUserId(OAuth2User oauthUser, Map<String, Object> attributes) {
        Object sub = attributes.get("sub");
        if (sub != null) {
            return sub.toString();
        }

        Object id = attributes.get("id");
        if (id != null) {
            return id.toString();
        }

        String fallback = oauthUser.getName();
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }

        throw new IllegalArgumentException("OAuth2 provider user id is missing");
    }

    private String resolveEmail(Map<String, Object> attributes) {
        Object email = attributes.get("email");
        if (email == null || email.toString().isBlank()) {
            throw new IllegalArgumentException("OAuth2 provider did not return email");
        }
        return email.toString();
    }

    private String resolveUsername(Map<String, Object> attributes, String email) {
        Object name = attributes.get("name");
        if (name != null && !name.toString().isBlank()) {
            return name.toString();
        }

        Object login = attributes.get("login");
        if (login != null && !login.toString().isBlank()) {
            return login.toString();
        }

        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void sendErrorRedirect(HttpServletResponse response, String message) throws IOException {
        String redirectTarget = redirectSuccessUri + "#error=" + encode(message);
        response.sendRedirect(redirectTarget);
    }
}
