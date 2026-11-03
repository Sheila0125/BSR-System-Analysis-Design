package com.bsr.bootstrap.usecase;

import com.bsr.platform.error.ApiException;
import com.bsr.rental.api.RentalBlockingProjection;
import com.bsr.scheduling.shift.api.FutureShiftProjection;
import com.bsr.storetransfer.coordination.api.StoreDeactivationGuard;
import com.bsr.storetransfer.transfer.api.TransferBlockingProjection;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Runs inside the store parent's exclusive-lock transaction. */
@Component
public class StoreStopGuard implements StoreDeactivationGuard {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Oslo");

    private final RentalBlockingProjection rentals;
    private final TransferBlockingProjection transfers;
    private final FutureShiftProjection shifts;
    private final Clock clock;

    public StoreStopGuard(RentalBlockingProjection rentals, TransferBlockingProjection transfers,
            FutureShiftProjection shifts, Clock clock) {
        this.rentals = rentals;
        this.transfers = transfers;
        this.shifts = shifts;
        this.clock = clock;
    }

    @Override
    public void validate(long storeId) {
        if (!rentals.unfinishedAtStore(storeId).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_HAS_UNFINISHED_RENTAL",
                    "An unfinished rental contract belongs to the store.");
        }
        if (!transfers.activeForStore(storeId).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_HAS_ACTIVE_TRANSFER",
                    "An unfinished equipment transfer involves the store.");
        }
        LocalDate businessToday = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (!shifts.futureActiveAtStore(storeId, businessToday).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_HAS_FUTURE_SHIFT",
                    "A future active shift belongs to the store.");
        }
    }
}
