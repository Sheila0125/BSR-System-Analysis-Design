package com.bsr.bootstrap.security;

import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.AccountCredentials;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.bsr.platform.error.ApiException;
import org.springframework.stereotype.Service;

@Service
public final class AccountUserDetailsService implements UserDetailsService {

    private final AccountAuthentication accountAuthentication;

    public AccountUserDetailsService(AccountAuthentication accountAuthentication) {
        this.accountAuthentication = accountAuthentication;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AccountCredentials account;
        try {
            account = accountAuthentication.loadByLoginName(username);
        } catch (ApiException exception) {
            throw new UsernameNotFoundException("The login name or password is incorrect.");
        }
        return new BsrAccountPrincipal(
                account.accountId(),
                account.loginName(),
                account.passwordHash(),
                account.enabled());
    }
}
