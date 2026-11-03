package com.bsr.bootstrap.config;

import com.bsr.bootstrap.security.SecurityConfiguration;
import com.bsr.bootstrap.security.SecurityProblemWriter;
import com.bsr.bootstrap.security.SessionAccountValidationFilter;
import com.bsr.identity.api.AccountAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = WebUiEmbeddedErrorTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.web.resources.static-locations=classpath:/fixtures/deployment-web/")
class WebUiEmbeddedErrorTest {
    @LocalServerPort int port;

    @Test
    void realServletErrorDispatchPreserves404WithoutOpeningDirectErrorOrApiAccess() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            assertEquals(404, status(client, "/assets/missing.js"));
            assertEquals(401, status(client, "/error"));
            assertEquals(401, status(client, "/api/not-a-web-page"));
        }
    }

    private int status(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({SecurityConfiguration.class, WebUiConfiguration.class,
            SessionAccountValidationFilter.class, SecurityProblemWriter.class})
    static class TestApplication {
        @Bean AccountAuthentication accountAuthentication() { return mock(AccountAuthentication.class); }
        @Bean UserDetailsService userDetailsService() {
            return name -> User.withUsername(name).password("{noop}not-used").roles("EMPLOYEE").build();
        }
    }
}
