package com.bsr.customerrequest.infrastructure.persistence;

import com.bsr.customerrequest.application.CustomerRequestPersistence;
import com.bsr.customerrequest.application.CustomerRequestPersistence.SupplementCycle;
import com.bsr.customerrequest.domain.CustomerProfile;
import com.bsr.customerrequest.domain.RentalRequest;
import com.bsr.customerrequest.domain.RentalRequestItem;
import com.bsr.customerrequest.domain.RentalRequestStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCustomerRequestPersistence implements CustomerRequestPersistence {

    private static final List<String> AWAITING_STATUSES = List.of("PENDING", "NEEDS_INFO");

    private final CustomerRepository customerRepository;
    private final RentalRequestRepository requestRepository;
    private final RentalRequestItemRepository itemRepository;
    private final RentalRequestSupplementRepository supplementRepository;
    private final JdbcTemplate jdbcTemplate;

    public JpaCustomerRequestPersistence(
            CustomerRepository customerRepository,
            RentalRequestRepository requestRepository,
            RentalRequestItemRepository itemRepository,
            RentalRequestSupplementRepository supplementRepository,
            JdbcTemplate jdbcTemplate) {
        this.customerRepository = customerRepository;
        this.requestRepository = requestRepository;
        this.itemRepository = itemRepository;
        this.supplementRepository = supplementRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<CustomerProfile> findCustomer(long customerId) {
        return customerRepository.findById(customerId).map(this::toProfile);
    }

    @Override
    public Optional<CustomerProfile> findCustomerByNumber(String customerNumber) {
        return customerRepository.findByCustomerNumber(customerNumber).map(this::toProfile);
    }

    @Override
    public Optional<CustomerProfile> findCustomerByEmail(String email) {
        return customerRepository.findByEmail(email).map(this::toProfile);
    }

    @Override
    public Optional<CustomerProfile> findCustomerForUpdate(long customerId) {
        return customerRepository.findByIdForUpdate(customerId).map(this::toProfile);
    }

    @Override
    public void saveCustomer(CustomerProfile profile) {
        CustomerRow row = customerRepository.findByIdForUpdate(profile.customerId())
                .orElseThrow(() -> new IllegalStateException("Locked customer disappeared during update."));
        row.applyProfile(profile);
        customerRepository.flush();
    }

    @Override
    public boolean hasAwaitingRequest(long customerId) {
        return requestRepository.existsByCustomerIdAndRequestStatusIn(customerId, AWAITING_STATUSES);
    }

    @Override
    public long saveRequest(RentalRequest request) {
        RentalRequestRow saved = requestRepository.saveAndFlush(new RentalRequestRow(request));
        for (RentalRequestItem item : request.items()) {
            itemRepository.save(new RentalRequestItemRow(saved.requestId(), item));
        }
        itemRepository.flush();
        return saved.requestId();
    }

    @Override
    public Optional<StoredRentalRequest> findOwnedRequest(long requestId, long customerId) {
        return requestRepository.findByRequestIdAndCustomerId(requestId, customerId)
                .map(this::toStored);
    }

    @Override
    public List<StoredRentalRequest> findOwnedRequests(long customerId) {
        return requestRepository.findByCustomerIdOrderBySubmittedAtDescRequestIdDesc(customerId)
                .stream()
                .map(this::toStored)
                .toList();
    }

    @Override
    public Optional<StoredRentalRequest> findRequestForUpdate(long requestId) {
        return requestRepository.findByIdForUpdate(requestId)
                .map(this::toStored);
    }

    @Override
    public List<StoredRentalRequest> findStoreRequests(long storeId, List<RentalRequestStatus> statuses) {
        List<String> statusCodes = statuses.stream().map(Enum::name).toList();
        return requestRepository
                .findByServiceStoreIdAndRequestStatusInOrderBySubmittedAtAscRequestIdAsc(storeId, statusCodes)
                .stream()
                .map(this::toStored)
                .toList();
    }

    @Override
    public void saveRequestState(long requestId, RentalRequest request) {
        RentalRequestRow row = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalStateException("Locked rental request disappeared during update."));
        row.updateState(request);
        requestRepository.flush();
    }

    @Override
    public List<SupplementCycle> listSupplements(long requestId) {
        return supplementRepository.findByRequestIdOrderByCycleNumberAsc(requestId).stream()
                .map(row -> new SupplementCycle(
                        row.supplementId, row.cycleNumber, row.requestedByEmployeeId,
                        row.reason, row.requestedAt, row.customerResponse, row.respondedAt))
                .toList();
    }

    @Override
    public void addSupplement(long requestId, int cycleNumber, long employeeId,
            String reason, LocalDateTime requestedAt) {
        supplementRepository.saveAndFlush(new RentalRequestSupplementRow(
                requestId, cycleNumber, employeeId, reason, requestedAt));
    }

    @Override
    public void answerSupplement(long supplementId, String response, LocalDateTime respondedAt) {
        RentalRequestSupplementRow row = supplementRepository.findById(supplementId)
                .orElseThrow(() -> new IllegalStateException("Supplement cycle disappeared during update."));
        row.answer(response, respondedAt);
        supplementRepository.flush();
    }

    @Override
    public Optional<ConversionContract> findConversionContract(long requestId) {
        return jdbcTemplate.query(
                "SELECT contract_id, customer_id, service_store_id FROM rental_contract WHERE source_request_id = ?",
                (result, rowNumber) -> new ConversionContract(
                        result.getLong("contract_id"), result.getLong("customer_id"),
                        result.getLong("service_store_id")),
                requestId).stream().findFirst();
    }

    private CustomerProfile toProfile(CustomerRow row) {
        return new CustomerProfile(
                row.customerId(),
                row.customerNumber(),
                row.fullName(),
                row.email(),
                row.phone(),
                row.address(),
                row.birthYear(),
                row.heightCm(),
                row.weightKg(),
                row.bootSize(),
                row.heightEstimated(),
                row.weightEstimated(),
                row.bootSizeEstimated());
    }

    private StoredRentalRequest toStored(RentalRequestRow row) {
        return new StoredRentalRequest(row.requestId(), toDomain(row), listSupplements(row.requestId()));
    }

    private RentalRequest toDomain(RentalRequestRow row) {
        List<RentalRequestItem> items = itemRepository
                .findByRequestIdOrderByRequestItemId(row.requestId())
                .stream()
                .map(item -> new RentalRequestItem(
                        item.equipmentCategory(),
                        item.quantity(),
                        item.fittingNote()))
                .toList();
        return RentalRequest.restore(
                row.requestNumber(),
                row.customerId(),
                row.serviceStoreId(),
                row.rentalStartDate(),
                row.rentalDays(),
                row.skillLevel(),
                row.submittedAt(),
                items,
                RentalRequestStatus.valueOf(row.requestStatus()),
                row.acceptedByEmployeeId());
    }
}
