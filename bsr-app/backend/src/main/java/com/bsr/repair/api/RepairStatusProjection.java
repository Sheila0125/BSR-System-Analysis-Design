package com.bsr.repair.api;

import java.util.Map;

/** Existing task states for one service store; contains no contractor details. */
public interface RepairStatusProjection {
    Map<String, Long> countsAtStore(long storeId);
}
