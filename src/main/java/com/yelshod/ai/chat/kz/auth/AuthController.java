package com.yelshod.ai.chat.kz.auth;

import com.yelshod.ai.chat.kz.auth.dto.AuthResponse;
import com.yelshod.ai.chat.kz.auth.dto.LoginRequest;
import com.yelshod.ai.chat.kz.auth.dto.MeResponse;
import com.yelshod.ai.chat.kz.auth.dto.RefreshTokenRequest;
import com.yelshod.ai.chat.kz.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AppPrincipal principal) {
        return authService.me(principal.userId());
    }
}
