package com.yelshod.ai.chat.kz.auth;

import com.yelshod.ai.chat.kz.common.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenMinutes;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.access-token-minutes:30}") long accessTokenMinutes) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("security.jwt.secret must be configured");
        }
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public JwtPayload issueAccessToken(Long userId, String email) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenMinutes, ChronoUnit.MINUTES);

        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();

        return new JwtPayload(token, expiresAt, userId, email);
    }

    public JwtPayload parseAndValidate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.parseLong(claims.getSubject());
            String email = claims.get("email", String.class);
            Date exp = claims.getExpiration();
            if (exp == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid access token");
            }

            return new JwtPayload(token, exp.toInstant(), userId, email);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid access token");
        }
    }

    public record JwtPayload(String token, Instant expiresAt, Long userId, String email) {
    }
}
