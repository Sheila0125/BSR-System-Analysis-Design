package com.bsr.bootstrap.config;

import com.bsr.bootstrap.security.SecurityConfiguration;
import com.bsr.bootstrap.security.SecurityProblemWriter;
import com.bsr.bootstrap.security.SessionAccountValidationFilter;
import com.bsr.identity.api.AccountAuthentication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebUiAccessTest.ProbeController.class,
        properties = "spring.web.resources.static-locations=classpath:/fixtures/deployment-web/")
@Import({SecurityConfiguration.class, SessionAccountValidationFilter.class, SecurityProblemWriter.class})
class WebUiAccessTest {
    @Autowired MockMvc mvc;
    @MockitoBean AccountAuthentication accountAuthentication;
    @MockitoBean UserDetailsService userDetailsService;

    @Test
    void anonymousBrowserCanOpenAndRefreshExistingWebPages() throws Exception {
        for (String path : new String[]{"/", "/identity/login", "/workbenches/manager",
                "/repair/contractor", "/rental/desk", "/scheduling/my-shifts"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void publicIndexAndAssetsLoadWithoutAUserSession() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("deployment-test-shell")));
        mvc.perform(get("/assets/probe.js")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("deploymentAsset")));
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }

    @Test
    void staticAccessDoesNotExposeBusinessApiOrBypassCsrf() throws Exception {
        mvc.perform(get("/api/deployment-probe")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/not-a-web-route")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/not-a-web-route").with(user("employee")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/identity/login")).andExpect(status().isForbidden());
        mvc.perform(post("/api/deployment-probe").with(user("employee")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/not-a-web-route").with(user("employee")))
                .andExpect(status().isNotFound());
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/deployment-probe")
        String probe() { return "business-api"; }
    }
}
