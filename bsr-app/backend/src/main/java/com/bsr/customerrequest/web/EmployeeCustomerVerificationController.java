package com.bsr.customerrequest.web;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.customerrequest.application.CustomerRequestService;
import com.bsr.customerrequest.application.CustomerRequestService.MeasurementUpdate;
import com.bsr.customerrequest.domain.CustomerProfile;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.platform.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employee/customers")
public class EmployeeCustomerVerificationController {

    private static final String MEASUREMENT = "[0-9]{1,3}(?:\\.[0-9]{1,2})?";
    private static final String BOOT_SIZE = "[0-9]{1,3}(?:\\.[0-9])?";

    private final CustomerRequestService service;
    private final AccountAuthentication accountAuthentication;

    public EmployeeCustomerVerificationController(
            CustomerRequestService service,
            AccountAuthentication accountAuthentication) {
        this.service = service;
        this.accountAuthentication = accountAuthentication;
    }

    @GetMapping("/verify")
    VerificationResponse verify(
            @RequestParam String storeId,
            @RequestParam(required = false) String customerNumber,
            @RequestParam(required = false) String email,
            Authentication authentication) {
        BsrAccountPrincipal principal = (BsrAccountPrincipal) authentication.getPrincipal();
        ActorContext actor = accountAuthentication.loadActor(principal.accountId());
        return VerificationResponse.from(service.findCustomerForEmployee(
                actor, positiveId(storeId, "storeId"), customerNumber, email));
    }

    @PostMapping("/{customerId}/review-measurements")
    VerificationResponse reviewMeasurements(
            @PathVariable String customerId,
            @RequestParam String storeId,
            @Valid @RequestBody MeasurementReviewRequest request,
            Authentication authentication) {
        BsrAccountPrincipal principal = (BsrAccountPrincipal) authentication.getPrincipal();
        ActorContext actor = accountAuthentication.loadActor(principal.accountId());
        try {
            return VerificationResponse.from(service.reviewCustomerMeasurements(
                    actor, positiveId(storeId, "storeId"), positiveId(customerId, "customerId"),
                    request.customerNumber(), measurements(request.expected()),
                    measurements(request.updated())));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage());
        }
    }

    private MeasurementUpdate measurements(MeasurementValues values) {
        return new MeasurementUpdate(
                decimal(values.heightCm()), values.heightEstimated(),
                decimal(values.weightKg()), values.weightEstimated(),
                decimal(values.bootSize()), values.bootSizeEstimated());
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private long positiveId(String value, String field) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw validation(field);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw validation(field);
        }
    }

    private ApiException validation(String field) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                field + " must be a positive decimal identifier.");
    }

    record MeasurementReviewRequest(
            @NotBlank @Size(max = 30) String customerNumber,
            @NotNull @Valid MeasurementValues expected,
            @NotNull @Valid MeasurementValues updated) {
    }

    record MeasurementValues(
            @Pattern(regexp = MEASUREMENT) String heightCm,
            @NotNull Boolean heightEstimated,
            @Pattern(regexp = MEASUREMENT) String weightKg,
            @NotNull Boolean weightEstimated,
            @Pattern(regexp = BOOT_SIZE) String bootSize,
            @NotNull Boolean bootSizeEstimated) {
    }

    record VerificationResponse(
            String customerId,
            String customerNumber,
            String fullName,
            String email,
            String phone,
            String heightCm,
            String weightKg,
            String bootSize,
            boolean heightEstimated,
            boolean weightEstimated,
            boolean bootSizeEstimated) {

        static VerificationResponse from(CustomerProfile profile) {
            return new VerificationResponse(
                    Long.toString(profile.customerId()),
                    profile.customerNumber(),
                    profile.fullName(),
                    profile.email(),
                    profile.phone(),
                    decimal(profile.heightCm()),
                    decimal(profile.weightKg()),
                    decimal(profile.bootSize()),
                    profile.heightEstimated(),
                    profile.weightEstimated(),
                    profile.bootSizeEstimated());
        }

        private static String decimal(BigDecimal value) {
            return value == null ? null : value.toPlainString();
        }
    }
}
