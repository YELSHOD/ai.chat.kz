package com.yelshod.ai.chat.kz.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final String redirectSuccessUri;

    public OAuth2AuthenticationFailureHandler(@Value("${app.auth.oauth2.redirect-success-uri:http://localhost:5173/auth/callback}") String redirectSuccessUri) {
        this.redirectSuccessUri = redirectSuccessUri;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? "OAuth2 authentication failed"
                : exception.getMessage();
        String redirectTarget = redirectSuccessUri + "#error=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
        response.sendRedirect(redirectTarget);
    }
}
