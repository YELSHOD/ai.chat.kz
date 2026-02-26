package com.yelshod.ai.chat.kz.auth;

import com.yelshod.ai.chat.kz.user.AppUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class TokenAuthService {

    private final AuthTokenRepository authTokenRepository;

    public TokenAuthService(AuthTokenRepository authTokenRepository) {
        this.authTokenRepository = authTokenRepository;
    }

    public Optional<AppUser> resolveUser(String token) {
        return authTokenRepository.findByTokenAndExpiresAtAfter(token, Instant.now())
                .map(AuthToken::getUser);
    }
}
