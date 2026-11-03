package com.bsr.scheduling.shift.api;

import java.util.Map;

/** Existing shift states for one store; contains no employee details. */
public interface ShiftStatusProjection {
    Map<String, Long> countsAtStore(long storeId);
}
