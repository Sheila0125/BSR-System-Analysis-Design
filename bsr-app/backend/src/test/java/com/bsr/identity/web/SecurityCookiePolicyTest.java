package com.bsr.identity.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bsr.bootstrap.security.SecurityConfiguration;
import jakarta.servlet.SessionCookieConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockServletContext;

class SecurityCookiePolicyTest {

    private final SecurityConfiguration configuration = new SecurityConfiguration();

    @Test
    @DisplayName("AC-UC00 production session cookie is Secure HttpOnly and SameSite Lax")
    void AC_UC00_productionSessionCookieIsHardened() throws Exception {
        MockServletContext servletContext = new MockServletContext();

        configuration.sessionCookieInitializer(new MockEnvironment()).onStartup(servletContext);

        SessionCookieConfig cookie = servletContext.getSessionCookieConfig();
        assertTrue(cookie.isSecure());
        assertTrue(cookie.isHttpOnly());
        assertEquals("Lax", cookie.getAttribute("SameSite"));
    }

    @Test
    @DisplayName("AC-UC00 local and test profiles retain HttpOnly SameSite cookies without requiring HTTPS")
    void AC_UC00_localSessionCookieAllowsLoopbackHttp() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        MockServletContext servletContext = new MockServletContext();

        configuration.sessionCookieInitializer(environment).onStartup(servletContext);

        SessionCookieConfig cookie = servletContext.getSessionCookieConfig();
        assertFalse(cookie.isSecure());
        assertTrue(cookie.isHttpOnly());
        assertEquals("Lax", cookie.getAttribute("SameSite"));
    }
}
