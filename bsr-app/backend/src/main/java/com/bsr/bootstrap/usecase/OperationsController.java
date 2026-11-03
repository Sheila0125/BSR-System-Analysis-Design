package com.bsr.bootstrap.usecase;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.platform.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations")
public class OperationsController {
    private final OperationsQuery query;
    private final AccountAuthentication accounts;

    public OperationsController(OperationsQuery query, AccountAuthentication accounts) {
        this.query = query;
        this.accounts = accounts;
    }

    @GetMapping
    public OperationsQuery.OperationsState store(@RequestParam String storeId,
            Authentication authentication) {
        long id = positiveId(storeId);
        var principal = (BsrAccountPrincipal) authentication.getPrincipal();
        var actor = accounts.loadActor(principal.accountId());
        return query.forStore(actor, id);
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
}
