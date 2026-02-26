package com.yelshod.ai.chat.kz.auth;

import com.yelshod.ai.chat.kz.auth.dto.AuthResponse;
import com.yelshod.ai.chat.kz.auth.dto.LoginRequest;
import com.yelshod.ai.chat.kz.auth.dto.MeResponse;
import com.yelshod.ai.chat.kz.auth.dto.RegisterRequest;
import com.yelshod.ai.chat.kz.common.ApiException;
import com.yelshod.ai.chat.kz.user.AppUser;
import com.yelshod.ai.chat.kz.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AppUserRepository appUserRepository,
                       AuthTokenRepository authTokenRepository,
                       PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (appUserRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already exists");
        }

        AppUser user = new AppUser();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setUsername(request.username().trim());
        user.setCreatedAt(Instant.now());

        AppUser savedUser = appUserRepository.save(user);
        return issueToken(savedUser);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        return issueToken(user);
    }

    private AuthResponse issueToken(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(30, ChronoUnit.DAYS);

        AuthToken authToken = new AuthToken();
        authToken.setToken(UUID.randomUUID().toString() + UUID.randomUUID());
        authToken.setUser(user);
        authToken.setCreatedAt(now);
        authToken.setExpiresAt(expiresAt);
        authTokenRepository.save(authToken);

        return new AuthResponse(
                authToken.getToken(),
                expiresAt,
                user.getId(),
                user.getEmail(),
                user.getUsername()
        );
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));

        return new MeResponse(user.getId(), user.getEmail(), user.getUsername());
    }
}
