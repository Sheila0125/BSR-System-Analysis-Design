package com.bsr.rental.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExternalTransactionResultTest {
    @Test @DisplayName("AC-R03 same-content equality includes charge, type, cents, result and third-party time, not operator")
    void acR03ExactBusinessContent() {
        var time=LocalDateTime.of(2026,10,10,1,0);
        var first=new ExternalTransactionResult(1,ExternalTransactionResult.Type.PAYMENT,"EXT-1",new BigDecimal("418.5"),ExternalTransactionResult.Status.SUCCEEDED,time);
        assertTrue(first.sameBusinessContent(new ExternalTransactionResult(1,first.type(),first.externalId(),new BigDecimal("418.50"),first.status(),time)));
        assertFalse(first.sameBusinessContent(new ExternalTransactionResult(2,first.type(),first.externalId(),first.amount(),first.status(),time)));
        assertFalse(first.sameBusinessContent(new ExternalTransactionResult(1,first.type(),first.externalId(),first.amount(),ExternalTransactionResult.Status.FAILED,time)));
        assertThrows(IllegalArgumentException.class,()->new ExternalTransactionResult(1,first.type()," EXT-1",first.amount(),first.status(),time));
        assertThrows(IllegalArgumentException.class,()->new ExternalTransactionResult(1,first.type(),"EXT-2",new BigDecimal("0.00"),first.status(),time));
        assertThrows(IllegalArgumentException.class,()->new ExternalTransactionResult(1,first.type(),"EXT-2",new BigDecimal("1.001"),first.status(),time));
        assertThrows(IllegalArgumentException.class,()->new ExternalTransactionResult(1,first.type(),"EXT-2",first.amount(),first.status(),null));
    }
}
