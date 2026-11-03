package com.bsr.rental.api;

import java.util.Map;

/** Existing contract states for one service store; contains no customer details. */
public interface RentalStatusProjection {
    Map<String, Long> countsAtStore(long storeId);
}
