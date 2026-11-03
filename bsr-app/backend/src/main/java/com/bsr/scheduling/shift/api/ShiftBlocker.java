package com.bsr.scheduling.shift.api;

import java.time.LocalDate;

public record ShiftBlocker(long shiftId, long storeId, LocalDate shiftDate) {
}
