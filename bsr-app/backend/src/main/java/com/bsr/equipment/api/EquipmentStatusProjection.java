package com.bsr.equipment.api;

import java.util.Map;

/** Existing equipment states at the current store; contains no item details. */
public interface EquipmentStatusProjection {
    Map<String, Long> countsAtStore(long storeId);
}
