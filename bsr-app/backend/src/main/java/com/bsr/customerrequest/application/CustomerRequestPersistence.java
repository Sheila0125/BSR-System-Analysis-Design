package com.bsr.customerrequest.application;

import com.bsr.customerrequest.domain.CustomerProfile;
import com.bsr.customerrequest.domain.RentalRequest;
import com.bsr.customerrequest.domain.RentalRequestStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CustomerRequestPersistence {

    Optional<CustomerProfile> findCustomer(long customerId);

    Optional<CustomerProfile> findCustomerByNumber(String customerNumber);

    Optional<CustomerProfile> findCustomerByEmail(String email);

    Optional<CustomerProfile> findCustomerForUpdate(long customerId);

    void saveCustomer(CustomerProfile profile);

    boolean hasAwaitingRequest(long customerId);

    long saveRequest(RentalRequest request);

    Optional<StoredRentalRequest> findOwnedRequest(long requestId, long customerId);

    List<StoredRentalRequest> findOwnedRequests(long customerId);

    Optional<StoredRentalRequest> findRequestForUpdate(long requestId);

    List<StoredRentalRequest> findStoreRequests(long storeId, List<RentalRequestStatus> statuses);

    void saveRequestState(long requestId, RentalRequest request);

    List<SupplementCycle> listSupplements(long requestId);

    void addSupplement(long requestId, int cycleNumber, long employeeId,
            String reason, LocalDateTime requestedAt);

    void answerSupplement(long supplementId, String response, LocalDateTime respondedAt);

    Optional<ConversionContract> findConversionContract(long requestId);

    record StoredRentalRequest(long requestId, RentalRequest request, List<SupplementCycle> supplements) {
        public StoredRentalRequest(long requestId, RentalRequest request) {
            this(requestId, request, List.of());
        }

        public StoredRentalRequest {
            supplements = List.copyOf(supplements);
        }
    }

    record SupplementCycle(long supplementId, int cycleNumber, long employeeId, String reason,
            LocalDateTime requestedAt, String customerResponse, LocalDateTime respondedAt) {
    }

    record ConversionContract(long contractId, long customerId, long serviceStoreId) {
    }
}
