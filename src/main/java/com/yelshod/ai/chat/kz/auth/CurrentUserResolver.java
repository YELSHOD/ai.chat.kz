package com.yelshod.ai.chat.kz.auth;

import com.yelshod.ai.chat.kz.common.ApiException;
import com.yelshod.ai.chat.kz.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

    private final AppUserRepository appUserRepository;
    private final boolean authDisabled;

    public CurrentUserResolver(AppUserRepository appUserRepository,
                               @Value("${app.security.disable-auth:true}") boolean authDisabled) {
        this.appUserRepository = appUserRepository;
        this.authDisabled = authDisabled;
    }

    public Long resolveUserId(AppPrincipal principal) {
        if (principal != null) {
            return principal.userId();
        }

        if (!authDisabled) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        return appUserRepository.findFirstByOrderByIdAsc()
                .map(user -> user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "No users found. Register a user first when auth is disabled."));
    }
}
