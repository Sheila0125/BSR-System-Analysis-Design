package com.bsr.identity.web;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.RoleCode;
import com.bsr.identity.application.RoleGrantService;
import com.bsr.identity.application.RoleGrantService.RoleGrantView;
import com.bsr.platform.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/role-grants")
public class RoleGrantController {

    private final RoleGrantService roleGrantService;
    private final AccountAuthentication accountAuthentication;

    public RoleGrantController(
            RoleGrantService roleGrantService,
            AccountAuthentication accountAuthentication) {
        this.roleGrantService = roleGrantService;
        this.accountAuthentication = accountAuthentication;
    }

    @PostMapping
    RoleGrantView grant(@Valid @RequestBody GrantRequest request, Authentication authentication) {
        ActorContext actor = currentActor(authentication);
        return roleGrantService.grant(
                actor,
                parseId(request.accountId(), "accountId"),
                parseRole(request.role()),
                request.storeId() == null ? null : parseId(request.storeId(), "storeId"),
                request.validFrom(),
                request.validTo());
    }

    @PostMapping("/{roleGrantId}/revoke")
    RoleGrantView revoke(
            @PathVariable String roleGrantId,
            Authentication authentication) {
        requirePositiveId(roleGrantId, "roleGrantId");
        return roleGrantService.revoke(currentActor(authentication), parseId(roleGrantId, "roleGrantId"));
    }

    private ActorContext currentActor(Authentication authentication) {
        BsrAccountPrincipal principal = (BsrAccountPrincipal) authentication.getPrincipal();
        return accountAuthentication.loadActor(principal.accountId());
    }

    private long parseId(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw badRequest(field + " must be a positive decimal identifier.");
        }
    }

    private void requirePositiveId(String value, String field) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw badRequest(field + " must be a positive decimal identifier.");
        }
    }

    private RoleCode parseRole(String value) {
        try {
            return RoleCode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw badRequest("role must be CUSTOMER, EMPLOYEE, MANAGER, or CONTRACTOR.");
        }
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    record GrantRequest(
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String accountId,
            @NotBlank String role,
            @Pattern(regexp = "[1-9][0-9]{0,18}") String storeId,
            @NotNull LocalDateTime validFrom,
            LocalDateTime validTo) {
    }
}
