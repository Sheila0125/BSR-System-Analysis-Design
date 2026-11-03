package com.bsr.bootstrap.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Oslo");

    @Bean
    Clock businessClock() {
        return Clock.system(BUSINESS_ZONE);
    }

    @Bean
    ZoneId businessZoneId() {
        return BUSINESS_ZONE;
    }
}
