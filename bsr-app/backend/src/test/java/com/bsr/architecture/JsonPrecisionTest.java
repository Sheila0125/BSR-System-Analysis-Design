package com.bsr.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsr.platform.types.EntityId;
import com.bsr.platform.types.MoneyAmount;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JsonPrecisionTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("S01 IDs above the JavaScript safe integer limit are JSON strings")
    void largeIdsAreStrings() throws Exception {
        String encoded = json.writeValueAsString(new EntityId(9_007_199_254_740_993L));
        assertThat(encoded).isEqualTo("\"9007199254740993\"");
        assertThat(json.readValue(encoded, EntityId.class).value())
                .isEqualTo(9_007_199_254_740_993L);
    }

    @Test
    @DisplayName("S01 money retains exact decimal scale in JSON")
    void moneyIsAnExactString() throws Exception {
        MoneyAmount amount = new MoneyAmount(new BigDecimal("9999999999.99"));
        String encoded = json.writeValueAsString(amount);
        assertThat(encoded).isEqualTo("\"9999999999.99\"");
        assertThat(json.readValue(encoded, MoneyAmount.class).value())
                .isEqualByComparingTo("9999999999.99");
    }
}
