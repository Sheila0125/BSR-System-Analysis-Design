package com.bsr.customerrequest.web;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.customerrequest.application.CustomerRequestPersistence.StoredRentalRequest;
import com.bsr.customerrequest.application.CustomerRequestPersistence.SupplementCycle;
import com.bsr.customerrequest.application.CustomerRequestService;
import com.bsr.customerrequest.domain.RentalRequest;
import com.bsr.customerrequest.domain.RentalRequestItem;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.platform.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employee/rental-requests")
public class EmployeeRentalRequestController {

    private final CustomerRequestService service;
    private final AccountAuthentication accountAuthentication;

    public EmployeeRentalRequestController(
            CustomerRequestService service,
            AccountAuthentication accountAuthentication) {
        this.service = service;
        this.accountAuthentication = accountAuthentication;
    }

    @GetMapping
    List<RentalRequestResponse> queue(
            @RequestParam String storeId,
            Authentication authentication) {
        return service.loadEmployeeQueue(currentActor(authentication), positiveId(storeId, "storeId"))
                .stream()
                .map(RentalRequestResponse::from)
                .toList();
    }

    @PostMapping("/{requestId}/request-more-information")
    RentalRequestResponse requestMoreInformation(
            @PathVariable String requestId,
            @Valid @RequestBody ReasonRequest body,
            Authentication authentication) {
        return RentalRequestResponse.from(service.requestMoreInformation(
                currentActor(authentication), positiveId(requestId, "requestId"), body.reason()));
    }

    @PostMapping("/{requestId}/accept")
    RentalRequestResponse accept(
            @PathVariable String requestId,
            Authentication authentication) {
        return RentalRequestResponse.from(service.accept(
                currentActor(authentication), positiveId(requestId, "requestId")));
    }

    @PostMapping("/{requestId}/mark-unavailable")
    RentalRequestResponse markUnavailable(
            @PathVariable String requestId,
            Authentication authentication) {
        return RentalRequestResponse.from(service.markUnavailable(
                currentActor(authentication), positiveId(requestId, "requestId")));
    }

    private ActorContext currentActor(Authentication authentication) {
        BsrAccountPrincipal principal = (BsrAccountPrincipal) authentication.getPrincipal();
        return accountAuthentication.loadActor(principal.accountId());
    }

    private long positiveId(String value, String field) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw validation(field + " must be a positive decimal identifier.");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw validation(field + " must be a positive decimal identifier.");
        }
    }

    private ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    record RentalRequestResponse(
            String requestId,
            String requestNumber,
            String customerId,
            String serviceStoreId,
            String rentalStartDate,
            int rentalDays,
            String skillLevel,
            String submittedAt,
            String status,
            String acceptedByEmployeeId,
            List<ItemResponse> items,
            List<SupplementResponse> supplements) {

        static RentalRequestResponse from(StoredRentalRequest stored) {
            RentalRequest request = stored.request();
            return new RentalRequestResponse(
                    Long.toString(stored.requestId()),
                    request.requestNumber(),
                    Long.toString(request.customerId()),
                    Long.toString(request.serviceStoreId()),
                    request.rentalStartDate().toString(),
                    request.rentalDays(),
                    request.skillLevel(),
                    request.submittedAt().toString(),
                    request.status().name(),
                    request.acceptedByEmployeeId() == null
                            ? null
                            : Long.toString(request.acceptedByEmployeeId()),
                    request.items().stream().map(ItemResponse::from).toList(),
                    stored.supplements().stream().map(SupplementResponse::from).toList());
        }
    }

    record ItemResponse(String equipmentCategory, int quantity, String fittingNote) {

        static ItemResponse from(RentalRequestItem item) {
            return new ItemResponse(item.equipmentCategory(), item.quantity(), item.fittingNote());
        }
    }

    record SupplementResponse(int cycleNumber, String reason, String requestedAt,
            String customerResponse, String respondedAt) {

        static SupplementResponse from(SupplementCycle cycle) {
            return new SupplementResponse(
                    cycle.cycleNumber(), cycle.reason(), cycle.requestedAt().toString(),
                    cycle.customerResponse(),
                    cycle.respondedAt() == null ? null : cycle.respondedAt().toString());
        }
    }
}
