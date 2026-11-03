package com.bsr.audit.web;

import com.bsr.audit.application.AuditQueryService;
import com.bsr.audit.application.AuditQueryService.AuditEventView;
import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.platform.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-events")
public class AuditEventController {

    private final AuditQueryService auditQueryService;
    private final AccountAuthentication accountAuthentication;

    public AuditEventController(
            AuditQueryService auditQueryService,
            AccountAuthentication accountAuthentication) {
        this.auditQueryService = auditQueryService;
        this.accountAuthentication = accountAuthentication;
    }

    @GetMapping
    List<AuditEventView> findByObject(
            @RequestParam String objectType,
            @RequestParam String objectId,
            Authentication authentication) {
        if (!objectType.matches("[A-Z][A-Z0-9_]{0,39}")) {
            throw badRequest("objectType must be an uppercase technical code of at most 40 characters.");
        }
        if (!objectId.matches("[1-9][0-9]{0,18}")) {
            throw badRequest("objectId must be a positive decimal identifier.");
        }
        BsrAccountPrincipal principal = (BsrAccountPrincipal) authentication.getPrincipal();
        ActorContext actor = accountAuthentication.loadActor(principal.accountId());
        return auditQueryService.findByObject(actor, objectType, parseId(objectId));
    }

    private long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "objectId must be a positive decimal identifier.");
        }
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }
}
