package com.bsr.bootstrap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebUiConfiguration implements WebMvcConfigurer {

    public static String[] pagePaths() {
        return new String[]{
                "/", "/identity/login", "/identity/session", "/identity/role-grants",
                "/customer-requests/employee-queue", "/customer-requests/verify-customer",
                "/equipment/available", "/equipment/catalog", "/rental/desk", "/rental/returns", "/rental/adjustments",
                "/repair/employee", "/repair/management", "/repair/contractor",
                "/storetransfer/management", "/storetransfer/handoff",
                "/scheduling/management", "/scheduling/my-shifts", "/audit/events",
                "/workbenches/employee", "/workbenches/manager", "/entry/miniprogram"
        };
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String path : pagePaths()) {
            registry.addViewController(path).setViewName("forward:/index.html");
        }
    }
}
