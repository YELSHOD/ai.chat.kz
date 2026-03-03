package com.yelshod.ai.chat.kz.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByTokenAndExpiresAtAfter(String token, Instant now);

    void deleteByToken(String token);

    void deleteByUserId(Long userId);
}
