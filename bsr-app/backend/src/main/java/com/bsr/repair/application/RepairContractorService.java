package com.bsr.repair.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.repair.contractor.api.ContractorAccess;
import com.bsr.repair.domain.RepairRatePeriod;
import com.bsr.repair.domain.RepairServiceCharge;
import com.bsr.repair.domain.RepairTaskStatus;
import com.bsr.scheduling.employee.api.EmployeeAccess;
import com.bsr.storetransfer.store.api.StoreAccess;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepairContractorService {

    private final RepairManagementPersistence management;
    private final RepairPersistence repairs;
    private final StoreAccess stores;
    private final EmployeeAccess employees;
    private final ContractorAccess contractors;
    private final Authorization authorization;
    private final AuditAppender audit;
    private final Clock clock;

    public RepairContractorService(RepairManagementPersistence management,
            RepairPersistence repairs, StoreAccess stores, EmployeeAccess employees,
            ContractorAccess contractors, Authorization authorization,
            AuditAppender audit, Clock clock) {
        this.management = management;
        this.repairs = repairs;
        this.stores = stores;
        this.employees = employees;
        this.contractors = contractors;
        this.authorization = authorization;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Contractor create(ActorContext actor, long storeId, String name,
            String contactName, String email, String phone) {
        return create(actor, storeId, name, contactName, email, phone, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Contractor create(ActorContext actor, long storeId, String name,
            String contactName, String email, String phone, String postalAddress) {
        lockManager(actor, storeId);
        validateContact(name, contactName, email, phone);
        validatePostalAddress(postalAddress);
        String number = "RC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        var saved = management.insertContractor(number, name.trim(), trimOptional(contactName),
                email.trim(), trimOptional(phone), trimOptional(postalAddress));
        audit.appendSuccess(actor, "REPAIR_CONTRACTOR", saved.id(),
                "REPAIR_CONTRACTOR_CREATED", "");
        return saved;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Contractor update(ActorContext actor, long storeId, long contractorId,
            String name, String contactName, String email, String phone) {
        return update(actor, storeId, contractorId, name, contactName, email, phone, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Contractor update(ActorContext actor, long storeId, long contractorId,
            String name, String contactName, String email, String phone, String postalAddress) {
        lockManager(actor, storeId);
        validateContact(name, contactName, email, phone);
        validatePostalAddress(postalAddress);
        contractors.lockForDeactivation(contractorId);
        var saved = management.updateContractor(contractorId, name.trim(),
                trimOptional(contactName), email.trim(), trimOptional(phone),
                postalAddress == null ? null : postalAddress.trim());
        audit.appendSuccess(actor, "REPAIR_CONTRACTOR", contractorId,
                "REPAIR_CONTRACTOR_UPDATED", "");
        return saved;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Contractor deactivate(ActorContext actor, long storeId, long contractorId) {
        lockManager(actor, storeId);
        contractors.lockForDeactivation(contractorId);
        if (management.hasUnfinishedAssignment(contractorId)) {
            throw new RepairOperationException(
                    RepairOperationException.Code.CONTRACTOR_HAS_UNFINISHED_TASKS,
                    "Contractor has unfinished repair tasks.");
        }
        var saved = management.deactivateContractor(contractorId);
        audit.appendSuccess(actor, "REPAIR_CONTRACTOR", contractorId,
                "REPAIR_CONTRACTOR_DEACTIVATED", "");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<RepairViews.Contractor> list(ActorContext actor, long storeId) {
        requireManager(actor, storeId);
        return management.listContractors();
    }

    @Transactional(readOnly = true)
    public List<RepairViews.Rate> rates(ActorContext actor, long storeId, long contractorId) {
        requireManager(actor, storeId);
        if (management.findContractor(contractorId).isEmpty()) {
            throw new com.bsr.repair.contractor.api.ContractorAccessException(
                    com.bsr.repair.contractor.api.ContractorAccessException.Code.CONTRACTOR_NOT_FOUND,
                    "Contractor does not exist.");
        }
        return management.listRates(contractorId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Rate addRate(ActorContext actor, long storeId, RepairRatePeriod period) {
        if (period == null) {
            throw new IllegalArgumentException("Repair rate period is required.");
        }
        lockManager(actor, storeId);
        contractors.lockForRateChange(period.contractorId());
        for (var existing : management.listRates(period.contractorId())) {
            var old = new RepairRatePeriod(existing.contractorId(), existing.effectiveFrom(),
                    existing.effectiveTo(), existing.hourlyRate());
            if (period.overlaps(old)) {
                throw new RepairOperationException(RepairOperationException.Code.RATE_OVERLAP,
                        "Historical repair rate periods overlap.");
            }
        }
        var saved = management.insertRate(period, now());
        for (long taskId : management.backfillUnratedLabor(saved.id(), period)) {
            var task = repairs.lockTask(taskId).orElseThrow();
            if (task.task().status() != RepairTaskStatus.PENDING_CHARGE
                    || repairs.findCharge(taskId).isPresent()) {
                continue;
            }
            var labors = repairs.findLabors(taskId);
            if (!labors.isEmpty() && labors.stream().allMatch(labor -> labor.laborAmount() != null)) {
                repairs.createPendingCharge(taskId, RepairServiceCharge.fromLabors(labors));
            }
        }
        audit.appendSuccess(actor, "REPAIR_RATE", saved.id(),
                "REPAIR_RATE_CREATED", "contractorId=" + period.contractorId());
        return saved;
    }

    private void lockManager(ActorContext actor, long storeId) {
        requireManager(actor, storeId);
        stores.lockForUse(storeId);
        employees.lockForUse(actor.identityId());
    }

    private void requireManager(ActorContext actor, long storeId) {
        authorization.require(actor, ActionPermission.MANAGER_CONTRACTOR_AND_RATE, storeId);
        if (actor == null || actor.identityType() != IdentityType.EMPLOYEE
                || actor.identityId() <= 0 || storeId <= 0) {
            throw new IllegalArgumentException("A manager employee and positive storeId are required.");
        }
    }

    private static void validateContact(String name, String contactName, String email, String phone) {
        if (name == null || name.isBlank() || name.trim().length() > 120
                || email == null || email.isBlank() || email.trim().length() > 255
                || !email.contains("@") || contactName != null && contactName.trim().length() > 100
                || phone != null && phone.trim().length() > 40) {
            throw new IllegalArgumentException("Invalid repair contractor contact details.");
        }
    }

    private static void validatePostalAddress(String value) {
        if (value != null && value.trim().length() > 255) {
            throw new IllegalArgumentException("Postal address must not exceed 255 characters.");
        }
    }

    private static String trimOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).withNano(0);
    }
}
