package com.bsr.bootstrap.usecase;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.platform.error.ApiException;
import com.bsr.scheduling.employee.api.EmployeeAccessException;
import com.bsr.storetransfer.coordination.api.GuardedStoreDeactivation;
import com.bsr.storetransfer.store.api.StoreAccessException;
import com.bsr.storetransfer.store.api.StoreSnapshot;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/stores")
public class StoreDeactivationController {
    private final GuardedStoreDeactivation deactivation;
    private final AccountAuthentication accounts;

    public StoreDeactivationController(GuardedStoreDeactivation deactivation,
            AccountAuthentication accounts) {
        this.deactivation = deactivation;
        this.accounts = accounts;
    }

    @PostMapping("/{storeId}/deactivate")
    public StoreState deactivate(@PathVariable String storeId,
            @RequestBody(required = false) JsonNode body, Authentication authentication) {
        if (body != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "Store deactivation accepts no request body.");
        }
        long id = positiveId(storeId);
        var principal = (BsrAccountPrincipal) authentication.getPrincipal();
        var actor = accounts.loadActor(principal.accountId());
        try {
            return StoreState.from(deactivation.deactivate(actor, id));
        } catch (StoreAccessException exception) {
            HttpStatus status = exception.code() == StoreAccessException.Code.STORE_NOT_FOUND
                    ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
            throw new ApiException(status, exception.code().name(), exception.getMessage());
        } catch (EmployeeAccessException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.code().name(), exception.getMessage());
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_CONCURRENT_CONFLICT",
                    "Store data changed concurrently; reload and try again.");
        }
    }

    private static long positiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORE_ID",
                    "Store ID must be a positive decimal string.");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STORE_ID",
                    "Store ID is outside the supported range.");
        }
    }

    public record StoreState(String storeId, String businessStatus) {
        static StoreState from(StoreSnapshot store) {
            return new StoreState(Long.toString(store.storeId()),
                    store.active() ? "ACTIVE" : "INACTIVE");
        }
    }
}
