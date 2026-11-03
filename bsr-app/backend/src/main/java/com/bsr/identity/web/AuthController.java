package com.bsr.identity.web;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActiveRoleGrant;
import com.bsr.identity.api.ActorContext;
import com.bsr.platform.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AccountAuthentication accountAuthentication;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            AccountAuthentication accountAuthentication) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.accountAuthentication = accountAuthentication;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @PostMapping("/login")
    MeResponse login(
            @Valid @RequestBody LoginRequest login,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.loginName(), login.password()));
        } catch (AuthenticationException exception) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "BAD_CREDENTIALS",
                    "The login name or password is incorrect.");
        }

        BsrAccountPrincipal principal = (BsrAccountPrincipal) authentication.getPrincipal();
        ActorContext actor = accountAuthentication.loadActor(principal.accountId());
        HttpSession existing = request.getSession(false);
        if (existing == null) {
            request.getSession(true);
        } else {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return MeResponse.from(actor);
    }

    @GetMapping("/me")
    MeResponse me(Authentication authentication) {
        BsrAccountPrincipal principal = (BsrAccountPrincipal) authentication.getPrincipal();
        return MeResponse.from(accountAuthentication.loadActor(principal.accountId()));
    }

    record LoginRequest(
            @NotBlank @Size(max = 100) String loginName,
            @NotBlank @Size(max = 255) String password) {
    }

    record CsrfResponse(String headerName, String parameterName, String token) {
    }

    record GrantResponse(String role, String storeId) {

        static GrantResponse from(ActiveRoleGrant grant) {
            return new GrantResponse(
                    grant.role().name(),
                    grant.storeId() == null ? null : grant.storeId().toString());
        }
    }

    record MeResponse(
            String accountId,
            String loginName,
            String identityType,
            String identityId,
            List<GrantResponse> grants) {

        static MeResponse from(ActorContext actor) {
            return new MeResponse(
                    Long.toString(actor.accountId()),
                    actor.loginName(),
                    actor.identityType().name(),
                    Long.toString(actor.identityId()),
                    actor.activeGrants().stream().map(GrantResponse::from).toList());
        }
    }
}
