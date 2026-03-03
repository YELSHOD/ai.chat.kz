package com.yelshod.ai.chat.kz.auth;

import com.yelshod.ai.chat.kz.auth.dto.AuthResponse;
import com.yelshod.ai.chat.kz.auth.dto.EmailVerificationRequest;
import com.yelshod.ai.chat.kz.auth.dto.ForgotPasswordRequest;
import com.yelshod.ai.chat.kz.auth.dto.ForgotPasswordResponse;
import com.yelshod.ai.chat.kz.auth.dto.LoginRequest;
import com.yelshod.ai.chat.kz.auth.dto.MessageResponse;
import com.yelshod.ai.chat.kz.auth.dto.MeResponse;
import com.yelshod.ai.chat.kz.auth.dto.RefreshTokenRequest;
import com.yelshod.ai.chat.kz.auth.dto.ResendVerificationRequest;
import com.yelshod.ai.chat.kz.auth.dto.RegisterRequest;
import com.yelshod.ai.chat.kz.auth.dto.ResetPasswordRequest;
import com.yelshod.ai.chat.kz.auth.dto.VerificationChallengeResponse;
import com.yelshod.ai.chat.kz.common.ApiException;
import com.yelshod.ai.chat.kz.user.AppUser;
import com.yelshod.ai.chat.kz.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final AuthTokenRepository authTokenRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final AuthEmailService authEmailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long passwordResetTtlMinutes;
    private final boolean exposePasswordResetToken;
    private final long emailVerificationTtlMinutes;
    private final boolean exposeEmailVerificationToken;
    private final String emailVerificationConfirmUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AppUserRepository appUserRepository,
                       AuthTokenRepository authTokenRepository,
                       OAuthAccountRepository oAuthAccountRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       EmailVerificationTokenRepository emailVerificationTokenRepository,
                       AuthEmailService authEmailService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${app.auth.password-reset.ttl-minutes:30}") long passwordResetTtlMinutes,
                       @Value("${app.auth.password-reset.expose-token:false}") boolean exposePasswordResetToken,
                       @Value("${app.auth.email-verification.ttl-minutes:1440}") long emailVerificationTtlMinutes,
                       @Value("${app.auth.email-verification.expose-token:false}") boolean exposeEmailVerificationToken,
                       @Value("${app.auth.email-verification.confirm-url:http://localhost:5173/verify-email}") String emailVerificationConfirmUrl) {
        this.appUserRepository = appUserRepository;
        this.authTokenRepository = authTokenRepository;
        this.oAuthAccountRepository = oAuthAccountRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.authEmailService = authEmailService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordResetTtlMinutes = passwordResetTtlMinutes;
        this.exposePasswordResetToken = exposePasswordResetToken;
        this.emailVerificationTtlMinutes = emailVerificationTtlMinutes;
        this.exposeEmailVerificationToken = exposeEmailVerificationToken;
        this.emailVerificationConfirmUrl = emailVerificationConfirmUrl;
    }

    @Transactional
    public VerificationChallengeResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (appUserRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already exists");
        }

        AppUser user = new AppUser();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setUsername(request.username().trim());
        user.setEmailVerified(false);
        user.setEmailVerifiedAt(null);
        user.setCreatedAt(Instant.now());

        return issueAndSendEmailVerification(appUserRepository.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Email is not verified", "EMAIL_NOT_VERIFIED");
        }

        return issueToken(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken().trim();
        AuthToken existing = authTokenRepository.findByTokenAndExpiresAtAfter(refreshToken, Instant.now())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        AppUser user = existing.getUser();
        authTokenRepository.delete(existing);
        return issueToken(user);
    }

    @Transactional
    public AuthResponse oauthLogin(String provider, String providerUserId, String email, String username) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedProviderUserId = providerUserId == null ? "" : providerUserId.trim();
        if (normalizedProviderUserId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OAuth2 subject is required", "OAUTH_SUBJECT_MISSING");
        }

        String normalizedEmail = normalizeEmail(email);
        OAuthAccount existingOAuth = oAuthAccountRepository
                .findByProviderAndProviderUserId(normalizedProvider, normalizedProviderUserId)
                .orElse(null);
        if (existingOAuth != null) {
            AppUser existingUser = existingOAuth.getUser();
            ensureEmailVerified(existingUser, Instant.now());
            return issueToken(existingOAuth.getUser());
        }

        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> createOAuthUser(normalizedEmail, username));
        ensureEmailVerified(user, Instant.now());

        OAuthAccount account = new OAuthAccount();
        account.setProvider(normalizedProvider);
        account.setProviderUserId(normalizedProviderUserId);
        account.setUser(user);
        account.setCreatedAt(Instant.now());
        oAuthAccountRepository.save(account);

        return issueToken(user);
    }

    @Transactional
    public AuthResponse verifyEmail(EmailVerificationRequest request) {
        String token = request.token().trim();
        if (token.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verification token is required", "VERIFY_TOKEN_REQUIRED");
        }

        Instant now = Instant.now();
        EmailVerificationToken verificationToken = emailVerificationTokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(sha256(token), now)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired verification token", "VERIFY_TOKEN_INVALID"));

        AppUser user = verificationToken.getUser();
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        appUserRepository.save(user);

        verificationToken.setUsedAt(now);
        emailVerificationTokenRepository.save(verificationToken);
        emailVerificationTokenRepository.deleteByUserIdAndUsedAtIsNull(user.getId());

        return issueToken(user);
    }

    @Transactional
    public VerificationChallengeResponse resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        Optional<AppUser> userOpt = appUserRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty() || userOpt.get().isEmailVerified()) {
            return new VerificationChallengeResponse(
                    "If an unverified account exists for this email, a new verification link has been sent.",
                    null,
                    null,
                    true
            );
        }

        return issueAndSendEmailVerification(userOpt.get());
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        Instant now = Instant.now();
        Optional<AppUser> userOpt = appUserRepository.findByEmail(normalizedEmail);

        if (userOpt.isEmpty()) {
            return new ForgotPasswordResponse(
                    "If an account exists for this email, a reset link has been generated.",
                    null,
                    null
            );
        }

        AppUser user = userOpt.get();
        passwordResetTokenRepository.deleteByUserIdAndUsedAtIsNull(user.getId());

        String resetTokenRaw = generateResetToken();
        Instant expiresAt = now.plus(passwordResetTtlMinutes, ChronoUnit.MINUTES);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(sha256(resetTokenRaw));
        resetToken.setCreatedAt(now);
        resetToken.setExpiresAt(expiresAt);
        passwordResetTokenRepository.save(resetToken);

        return new ForgotPasswordResponse(
                "If an account exists for this email, a reset link has been generated.",
                expiresAt,
                exposePasswordResetToken ? resetTokenRaw : null
        );
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String token = request.token().trim();
        if (token.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Reset token is required", "RESET_TOKEN_REQUIRED");
        }

        Instant now = Instant.now();
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(sha256(token), now)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired reset token", "RESET_TOKEN_INVALID"));

        AppUser user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        appUserRepository.save(user);

        resetToken.setUsedAt(now);
        passwordResetTokenRepository.save(resetToken);
        passwordResetTokenRepository.deleteByUserIdAndUsedAtIsNull(user.getId());
        authTokenRepository.deleteByUserId(user.getId());

        return new MessageResponse("Password has been reset successfully.");
    }

    private AuthResponse issueToken(AppUser user) {
        Instant now = Instant.now();
        Instant refreshExpiresAt = now.plus(30, ChronoUnit.DAYS);
        String refreshToken = UUID.randomUUID().toString() + UUID.randomUUID();

        AuthToken authToken = new AuthToken();
        authToken.setToken(refreshToken);
        authToken.setUser(user);
        authToken.setCreatedAt(now);
        authToken.setExpiresAt(refreshExpiresAt);
        authTokenRepository.save(authToken);

        JwtService.JwtPayload accessPayload = jwtService.issueAccessToken(user.getId(), user.getEmail());

        return new AuthResponse(
                accessPayload.token(),
                accessPayload.expiresAt(),
                refreshToken,
                refreshExpiresAt,
                user.getId(),
                user.getEmail(),
                user.getUsername()
        );
    }

    private AppUser createOAuthUser(String normalizedEmail, String username) {
        Instant now = Instant.now();
        AppUser user = new AppUser();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID() + UUID.randomUUID().toString()));
        user.setUsername(sanitizeUsername(username, normalizedEmail));
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        user.setCreatedAt(now);
        return appUserRepository.save(user);
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Email is required", "EMAIL_REQUIRED");
        }
        return email.trim().toLowerCase();
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OAuth2 provider is required", "OAUTH_PROVIDER_REQUIRED");
        }
        return provider.trim().toLowerCase();
    }

    private String sanitizeUsername(String username, String email) {
        String candidate = username == null ? "" : username.trim();
        if (candidate.isBlank()) {
            int atIndex = email.indexOf('@');
            candidate = atIndex > 0 ? email.substring(0, atIndex) : email;
        }
        if (candidate.length() > 32) {
            candidate = candidate.substring(0, 32);
        }
        return candidate.isBlank() ? "user" : candidate;
    }

    private String generateResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private VerificationChallengeResponse issueAndSendEmailVerification(AppUser user) {
        Instant now = Instant.now();
        emailVerificationTokenRepository.deleteByUserIdAndUsedAtIsNull(user.getId());

        String rawToken = generateResetToken();
        Instant expiresAt = now.plus(emailVerificationTtlMinutes, ChronoUnit.MINUTES);

        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUser(user);
        verificationToken.setTokenHash(sha256(rawToken));
        verificationToken.setCreatedAt(now);
        verificationToken.setExpiresAt(expiresAt);
        emailVerificationTokenRepository.save(verificationToken);

        String verificationLink = buildVerificationLink(rawToken);
        boolean emailSent = authEmailService.sendVerificationEmail(
                user.getEmail(),
                user.getUsername(),
                verificationLink,
                expiresAt
        );

        return new VerificationChallengeResponse(
                "Registration successful. Please verify your email to continue.",
                expiresAt,
                exposeEmailVerificationToken ? rawToken : null,
                emailSent
        );
    }

    private String buildVerificationLink(String rawToken) {
        String separator = emailVerificationConfirmUrl.contains("?") ? "&" : "?";
        return emailVerificationConfirmUrl + separator + "token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private void ensureEmailVerified(AppUser user, Instant now) {
        if (user.isEmailVerified()) {
            return;
        }
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        appUserRepository.save(user);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to process token hash", "TOKEN_HASH_ERROR");
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User not found"));

        return new MeResponse(user.getId(), user.getEmail(), user.getUsername(), user.isEmailVerified());
    }
}
