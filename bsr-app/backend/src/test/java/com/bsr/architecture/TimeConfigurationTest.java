package com.bsr.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsr.bootstrap.config.TimeConfiguration;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeConfigurationTest {

    @Test
    @DisplayName("S01 business calendar uses the approved Oslo time zone")
    void businessZoneIsOslo() {
        assertThat(TimeConfiguration.BUSINESS_ZONE).isEqualTo(ZoneId.of("Europe/Oslo"));
    }
}
