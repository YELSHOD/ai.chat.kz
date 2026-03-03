package com.yelshod.ai.chat.kz.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(String tokenHash, Instant now);

    void deleteByUserIdAndUsedAtIsNull(Long userId);
}
