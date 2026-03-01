package com.yelshod.ai.chat.kz.auth;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TokenAuthService {

    private final JwtService jwtService;

    public TokenAuthService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public Optional<AppPrincipal> resolvePrincipal(String token) {
        try {
            JwtService.JwtPayload payload = jwtService.parseAndValidate(token);
            return Optional.of(new AppPrincipal(payload.userId(), payload.email()));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }
}
