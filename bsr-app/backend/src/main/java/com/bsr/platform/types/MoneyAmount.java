package com.bsr.platform.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record MoneyAmount(BigDecimal value) {

    public MoneyAmount {
        if (value == null) {
            throw new IllegalArgumentException("Money amount is required");
        }
        value = value.setScale(2, RoundingMode.UNNECESSARY);
        if (value.precision() > 12) {
            throw new IllegalArgumentException("Money amount exceeds DECIMAL(12,2)");
        }
    }

    @JsonCreator
    public static MoneyAmount parse(String value) {
        return new MoneyAmount(new BigDecimal(value));
    }

    @JsonValue
    public String asString() {
        return value.toPlainString();
    }
}
