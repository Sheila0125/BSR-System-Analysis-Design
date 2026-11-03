package com.bsr.bootstrap.security;

import com.bsr.identity.api.AccountAuthentication;
import com.bsr.platform.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class SessionAccountValidationFilter extends OncePerRequestFilter {

    private final AccountAuthentication accountAuthentication;
    private final SecurityProblemWriter problemWriter;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public SessionAccountValidationFilter(
            AccountAuthentication accountAuthentication,
            SecurityProblemWriter problemWriter) {
        this.accountAuthentication = accountAuthentication;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof BsrAccountPrincipal principal) {
            try {
                accountAuthentication.loadActor(principal.accountId());
            } catch (ApiException exception) {
                logoutHandler.logout(request, response, authentication);
                problemWriter.write(
                        request,
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "Unauthorized",
                        exception.code(),
                        exception.getMessage());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
