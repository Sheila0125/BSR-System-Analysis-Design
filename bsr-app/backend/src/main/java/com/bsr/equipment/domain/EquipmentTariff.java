package com.bsr.equipment.domain;

import java.math.BigDecimal;
import java.util.Map;

/** BSR reference daily prices in NOK; contract snapshots and existing archives are never rewritten. */
public final class EquipmentTariff {
    private EquipmentTariff() { }

    public static Map<String, BigDecimal> dailyRates() {
        return Map.of("SKI", new BigDecimal("350.00"), "SNOWBOARD", new BigDecimal("350.00"),
                "BOOTS", new BigDecimal("180.00"), "POLES", new BigDecimal("60.00"),
                "GOGGLES", new BigDecimal("90.00"), "HELMET", new BigDecimal("80.00"));
    }
}
