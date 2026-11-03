package com.bsr.customerrequest.web;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.customerrequest.application.CustomerRequestPersistence.StoredRentalRequest;
import com.bsr.customerrequest.application.CustomerRequestPersistence.SupplementCycle;
import com.bsr.customerrequest.application.CustomerRequestService;
import com.bsr.customerrequest.application.CustomerRequestService.MeasurementUpdate;
import com.bsr.customerrequest.application.CustomerRequestService.Submission;
import com.bsr.customerrequest.domain.CustomerProfile;
import com.bsr.customerrequest.domain.RentalRequest;
import com.bsr.customerrequest.domain.RentalRequestItem;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.platform.error.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer")
public class CustomerRequestController {

    private static final String CENTIMETER_OR_KILOGRAM = "[0-9]{1,3}(?:\\.[0-9]{1,2})?";
    private static final String BOOT_SIZE = "[0-9]{1,3}(?:\\.[0-9])?";

    private final CustomerRequestService service;
    private final AccountAuthentication accountAuthentication;

    public CustomerRequestController(
            CustomerRequestService service,
            AccountAuthentication accountAuthentication) {
        this.service = service;
        this.accountAuthentication = accountAuthentication;
    }

    @GetMapping("/profile")
    ProfileResponse profile(Authentication authentication) {
        return ProfileResponse.from(service.loadOwnProfile(currentActor(authentication)));
    }

    @PostMapping("/profile/contact")
    ProfileResponse updateContact(@Valid @RequestBody ContactRequest request, Authentication authentication) {
        try {
            return ProfileResponse.from(service.updateOwnContactDetails(currentActor(authentication),
                    new CustomerRequestService.ContactUpdate(request.fullName(), request.email(), request.phone(),
                            request.address(), request.birthYear())));
        } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage());
        }
    }

    @PostMapping("/profile/measurements")
    ProfileResponse updateMeasurements(
            @Valid @RequestBody MeasurementRequest request,
            Authentication authentication) {
        try {
            return ProfileResponse.from(service.updateOwnMeasurements(
                    currentActor(authentication),
                    new MeasurementUpdate(
                            decimal(request.heightCm()),
                            request.heightEstimated(),
                            decimal(request.weightKg()),
                            request.weightEstimated(),
                            decimal(request.bootSize()),
                            request.bootSizeEstimated())));
        } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage());
        }
    }

    @GetMapping("/requests")
    List<RentalRequestResponse> requests(Authentication authentication) {
        return service.loadOwnedRequests(currentActor(authentication)).stream()
                .map(RentalRequestResponse::from)
                .toList();
    }

    @PostMapping("/requests")
    RentalRequestResponse submit(
            @Valid @RequestBody SubmitRequest request,
            Authentication authentication) {
        try {
            Submission submission = new Submission(
                    positiveId(request.serviceStoreId(), "serviceStoreId"),
                    request.rentalStartDate(),
                    request.rentalDays(),
                    request.skillLevel(),
                    request.items().stream()
                            .map(item -> new RentalRequestItem(
                                    item.equipmentCategory(),
                                    item.quantity(),
                                    item.fittingNote()))
                            .toList(),
                    request.confirmDuplicate());
            return RentalRequestResponse.from(service.submit(currentActor(authentication), submission));
        } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage());
        }
    }

    @GetMapping("/requests/{requestId}")
    RentalRequestResponse request(
            @PathVariable String requestId,
            Authentication authentication) {
        return RentalRequestResponse.from(service.loadOwnedRequest(
                currentActor(authentication),
                positiveId(requestId, "requestId")));
    }

    @PostMapping("/requests/{requestId}/resubmit-supplement")
    RentalRequestResponse resubmitSupplement(
            @PathVariable String requestId,
            @Valid @RequestBody SupplementResponseRequest body,
            Authentication authentication) {
        return RentalRequestResponse.from(service.resubmitSupplementedInformation(
                currentActor(authentication), positiveId(requestId, "requestId"), body.response()));
    }

    private ActorContext currentActor(Authentication authentication) {
        BsrAccountPrincipal principal = (BsrAccountPrincipal) authentication.getPrincipal();
        return accountAuthentication.loadActor(principal.accountId());
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
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

    // Omitted optional fields preserve existing values; explicit empty strings clear them.
    record ContactRequest(@NotBlank @Size(max = 100) String fullName,
            @NotBlank @Size(max = 255) String email, @NotBlank @Size(max = 40) String phone,
            @Size(max = 255) String address, @Size(max = 4) String birthYear) { }

    record MeasurementRequest(
            @Pattern(regexp = CENTIMETER_OR_KILOGRAM) String heightCm,
            @NotNull Boolean heightEstimated,
            @Pattern(regexp = CENTIMETER_OR_KILOGRAM) String weightKg,
            @NotNull Boolean weightEstimated,
            @Pattern(regexp = BOOT_SIZE) String bootSize,
            @NotNull Boolean bootSizeEstimated) {
    }

    record SubmitRequest(
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String serviceStoreId,
            @NotNull LocalDate rentalStartDate,
            @Min(1) int rentalDays,
            @NotBlank @Size(max = 20) String skillLevel,
            @NotEmpty @Size(max = 20) List<@Valid ItemRequest> items,
            @NotNull Boolean confirmDuplicate) {
    }

    record ItemRequest(
            @NotBlank @Size(max = 40) String equipmentCategory,
            @Min(1) int quantity,
            @Size(max = 255) String fittingNote) {
    }

    record SupplementResponseRequest(@NotBlank @Size(max = 500) String response) {
    }

    record ProfileResponse(
            String customerId,
            String customerNumber,
            String fullName,
            String email,
            String phone,
            String address,
            Short birthYear,
            String heightCm,
            String weightKg,
            String bootSize,
            boolean heightEstimated,
            boolean weightEstimated,
            boolean bootSizeEstimated) {

        static ProfileResponse from(CustomerProfile profile) {
            return new ProfileResponse(
                    Long.toString(profile.customerId()),
                    profile.customerNumber(),
                    profile.fullName(),
                    profile.email(),
                    profile.phone(),
                    profile.address(),
                    profile.birthYear(),
                    decimalString(profile.heightCm()),
                    decimalString(profile.weightKg()),
                    decimalString(profile.bootSize()),
                    profile.heightEstimated(),
                    profile.weightEstimated(),
                    profile.bootSizeEstimated());
        }
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

    private static String decimalString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
