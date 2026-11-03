package com.bsr.customerrequest.api;

import com.bsr.customerrequest.domain.RentalRequestStatus;
import java.time.LocalDate;
import java.util.List;

public record RentalRequestSnapshot(
        long requestId,
        long customerId,
        long serviceStoreId,
        LocalDate rentalStartDate,
        int rentalDays,
        String skillLevel,
        RentalRequestStatus status,
        Long acceptedByEmployeeId,
        List<RequestedItem> items) {

    public RentalRequestSnapshot {
        items = List.copyOf(items);
    }

    public record RequestedItem(String equipmentCategory, int quantity, String fittingNote) {
    }
}
