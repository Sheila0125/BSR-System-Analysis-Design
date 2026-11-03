package com.bsr.storetransfer.coordination.infrastructure;

import com.bsr.platform.error.ApiException;
import com.bsr.storetransfer.coordination.api.StoreDeactivationGuard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
public class StoreDeactivationGuardConfiguration {
    @Bean
    @ConditionalOnMissingBean(StoreDeactivationGuard.class)
    StoreDeactivationGuard denyDeactivationWithoutT09() {
        return storeId -> {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_STOP_GUARD_UNAVAILABLE",
                    "Cross-module store deactivation guard is not installed.");
        };
    }
}
