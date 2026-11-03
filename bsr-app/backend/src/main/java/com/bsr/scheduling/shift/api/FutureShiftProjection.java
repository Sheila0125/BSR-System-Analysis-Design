package com.bsr.scheduling.shift.api;

import java.time.LocalDate;
import java.util.List;

public interface FutureShiftProjection {
    List<ShiftBlocker> futureActiveAtStore(long storeId, LocalDate businessToday);
}
