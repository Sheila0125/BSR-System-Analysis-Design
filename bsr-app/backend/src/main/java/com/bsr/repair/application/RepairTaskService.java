package com.bsr.repair.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.equipment.api.EquipmentOperations;
import com.bsr.equipment.api.InspectionOperations;
import com.bsr.equipment.api.InspectionOutcome;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.repair.contractor.api.ContractorAccess;
import com.bsr.repair.domain.RepairTask;
import com.bsr.repair.domain.RepairLabor;
import com.bsr.repair.domain.RepairTaskStatus;
import com.bsr.repair.domain.RepairServiceCharge;
import com.bsr.scheduling.employee.api.EmployeeAccess;
import com.bsr.storetransfer.store.api.StoreAccess;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepairTaskService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Oslo");

    private final RepairPersistence persistence;
    private final StoreAccess stores;
    private final EmployeeAccess employees;
    private final ContractorAccess contractors;
    private final EquipmentOperations equipment;
    private final InspectionOperations inspections;
    private final Authorization authorization;
    private final AuditAppender audit;
    private final Clock clock;

    public RepairTaskService(RepairPersistence persistence, StoreAccess stores,
            EmployeeAccess employees, ContractorAccess contractors,
            EquipmentOperations equipment, InspectionOperations inspections,
            Authorization authorization,
            AuditAppender audit, Clock clock) {
        this.persistence = persistence;
        this.stores = stores;
        this.employees = employees;
        this.contractors = contractors;
        this.equipment = equipment;
        this.inspections = inspections;
        this.authorization = authorization;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Task create(ActorContext actor, long storeId, long equipmentId,
            String faultDescription, Long contractorId) {
        requireEmployee(actor);
        requirePositive(storeId, "storeId");
        requirePositive(equipmentId, "equipmentId");
        String fault = requiredText(faultDescription, "faultDescription");
        authorization.require(actor, ActionPermission.EMPLOYEE_REPAIR_PROCESS, storeId);
        stores.lockForUse(storeId);
        employees.lockForUse(actor.identityId());
        if (contractorId != null) {
            contractors.lockForAssignment(contractorId);
        }
        var lockedEquipment = equipment.lockForRepairTaskCreation(actor, equipmentId);
        if (lockedEquipment.currentStoreId() != storeId) {
            throw new RepairOperationException(RepairOperationException.Code.STORE_CONFLICT,
                    "Repair equipment is not at the requested store.");
        }
        if (persistence.hasUnfinishedTask(equipmentId)) {
            throw new RepairOperationException(RepairOperationException.Code.UNFINISHED_TASK_EXISTS,
                    "The equipment already has an unfinished repair task.");
        }
        RepairTask task = RepairTask.create(contractorId);
        String number = "RT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        var saved = persistence.insertTask(number, equipmentId, actor.identityId(), storeId,
                LocalDate.now(clock.withZone(BUSINESS_ZONE)), fault, task);
        audit.appendSuccess(actor, "REPAIR_TASK", saved.id(), "REPAIR_TASK_CREATED",
                "equipmentId=" + equipmentId + ";storeId=" + storeId);
        return saved.view();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Task assign(ActorContext actor, long taskId, long contractorId) {
        requireEmployee(actor);
        requirePositive(taskId, "taskId");
        requirePositive(contractorId, "contractorId");
        var route = persistence.findTask(taskId).orElseThrow(() -> notFound(taskId));
        authorization.require(actor, ActionPermission.EMPLOYEE_REPAIR_PROCESS, route.serviceStoreId());
        stores.lockForUse(route.serviceStoreId());
        employees.lockForUse(actor.identityId());
        contractors.lockForAssignment(contractorId);
        var locked = persistence.lockTask(taskId).orElseThrow(() -> notFound(taskId));
        if (locked.serviceStoreId() != route.serviceStoreId()) {
            throw new RepairOperationException(RepairOperationException.Code.STORE_CONFLICT,
                    "Repair task store changed while assigning.");
        }
        try {
            locked.task().assign(contractorId);
        } catch (IllegalStateException invalidState) {
            throw new RepairOperationException(RepairOperationException.Code.TASK_STATE_CONFLICT,
                    invalidState.getMessage());
        }
        persistence.saveTask(locked);
        audit.appendSuccess(actor, "REPAIR_TASK", taskId, "REPAIR_TASK_ASSIGNED",
                "contractorId=" + contractorId);
        return locked.view();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Task start(ActorContext actor, long taskId) {
        var route = routeForContractor(actor, taskId);
        stores.lockForUse(route.serviceStoreId());
        contractors.lockForAssignment(actor.identityId());
        var locked = lockForContractor(actor, taskId);
        try {
            locked.task().start(actor.identityId());
        } catch (IllegalStateException invalidState) {
            throw taskConflict(invalidState);
        }
        equipment.startRepair(actor, taskId, locked.equipmentId());
        persistence.saveTask(locked);
        audit.appendSuccess(actor, "REPAIR_TASK", taskId, "REPAIR_TASK_STARTED", "");
        return locked.view();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Labor addLabor(ActorContext actor, long taskId, LocalDate repairDate,
            BigDecimal durationHours, String handlingResult, String note) {
        var route = routeForContractor(actor, taskId);
        stores.lockForUse(route.serviceStoreId());
        contractors.lockForAssignment(actor.identityId());
        var locked = lockForContractor(actor, taskId);
        if (locked.task().status() != RepairTaskStatus.IN_PROGRESS) {
            throw new RepairOperationException(RepairOperationException.Code.TASK_STATE_CONFLICT,
                    "Repair task is not in progress.");
        }
        var labor = RepairLabor.create(actor.identityId(), repairDate, durationHours,
                handlingResult, note);
        persistence.findRate(actor.identityId(), repairDate).ifPresent(rate ->
                labor.applyRate(rate.id(), rate.period()));
        var saved = persistence.insertLabor(taskId, labor);
        audit.appendSuccess(actor, "REPAIR_LABOR", saved.id(), "REPAIR_LABOR_ADDED",
                "repairTaskId=" + taskId);
        return saved;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Task submit(ActorContext actor, long taskId) {
        var route = routeForContractor(actor, taskId);
        stores.lockForUse(route.serviceStoreId());
        contractors.lockForAssignment(actor.identityId());
        var locked = lockForContractor(actor, taskId);
        try {
            locked.task().submit(actor.identityId(),
                    persistence.countUnsubmittedLabors(taskId, actor.identityId()));
        } catch (IllegalStateException invalidState) {
            throw taskConflict(invalidState);
        }
        equipment.submitRepair(actor, taskId, locked.equipmentId());
        persistence.markLaborsSubmitted(taskId, actor.identityId(), now());
        persistence.saveTask(locked);
        audit.appendSuccess(actor, "REPAIR_TASK", taskId, "REPAIR_TASK_SUBMITTED", "");
        return locked.view();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Task reject(ActorContext actor, long taskId, String reason) {
        var locked = lockForEmployee(actor, taskId);
        try {
            locked.task().reject(reason);
        } catch (IllegalStateException invalidState) {
            throw taskConflict(invalidState);
        }
        inspections.inspectRepairAcceptance(actor, taskId, locked.equipmentId(),
                InspectionOutcome.FAULT, reason.trim());
        var saved = persistence.saveAcceptance(locked, actor.identityId(), now());
        audit.appendSuccess(actor, "REPAIR_TASK", taskId, "REPAIR_TASK_REJECTED", "");
        return saved.view();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Task accept(ActorContext actor, long taskId) {
        var locked = lockForEmployee(actor, taskId);
        try {
            locked.task().accept();
        } catch (IllegalStateException invalidState) {
            throw taskConflict(invalidState);
        }
        inspections.inspectRepairAcceptance(actor, taskId, locked.equipmentId(),
                InspectionOutcome.NORMAL, null);
        var saved = persistence.saveAcceptance(locked, actor.identityId(), now());
        var labors = persistence.findLabors(taskId);
        if (!labors.isEmpty() && labors.stream().allMatch(labor -> labor.laborAmount() != null)) {
            persistence.createPendingCharge(taskId, RepairServiceCharge.fromLabors(labors));
        }
        audit.appendSuccess(actor, "REPAIR_TASK", taskId, "REPAIR_TASK_ACCEPTED", "");
        return saved.view();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RepairViews.Task confirmCharge(ActorContext actor, long taskId) {
        var locked = lockForEmployee(actor, taskId);
        if (locked.task().status() == RepairTaskStatus.COMPLETED) {
            return locked.view();
        }
        var charge = persistence.findCharge(taskId).orElseThrow(() ->
                new RepairOperationException(RepairOperationException.Code.TASK_STATE_CONFLICT,
                        "Historical rates are incomplete; service charge is not ready."));
        if (!"PENDING_CONFIRMATION".equals(charge.status())) {
            throw new RepairOperationException(RepairOperationException.Code.TASK_STATE_CONFLICT,
                    "Repair service charge is not pending confirmation.");
        }
        try {
            locked.task().complete(true);
        } catch (IllegalStateException invalidState) {
            throw taskConflict(invalidState);
        }
        persistence.confirmCharge(taskId, actor.identityId(), now());
        persistence.saveTask(locked);
        audit.appendSuccess(actor, "REPAIR_SERVICE_CHARGE", charge.id(),
                "REPAIR_CHARGE_CONFIRMED", "repairTaskId=" + taskId);
        return locked.view();
    }

    @Transactional(readOnly = true)
    public List<RepairViews.Task> employeeTasks(ActorContext actor, long storeId) {
        requireEmployee(actor);
        requirePositive(storeId, "storeId");
        authorization.require(actor, ActionPermission.EMPLOYEE_REPAIR_PROCESS, storeId);
        return persistence.findTasksByStore(storeId).stream()
                .map(RepairPersistence.StoredTask::view).toList();
    }

    @Transactional(readOnly = true)
    public RepairViews.Task employeeTask(ActorContext actor, long taskId) {
        var task = persistence.findTask(taskId).orElseThrow(() -> notFound(taskId));
        requireEmployee(actor);
        authorization.require(actor, ActionPermission.EMPLOYEE_REPAIR_PROCESS, task.serviceStoreId());
        return task.view();
    }

    @Transactional(readOnly = true)
    public List<RepairViews.Labor> employeeLabors(ActorContext actor, long taskId) {
        employeeTask(actor, taskId);
        return persistence.findLaborViewsByTask(taskId);
    }

    @Transactional(readOnly = true)
    public RepairViews.Charge employeeCharge(ActorContext actor, long taskId) {
        employeeTask(actor, taskId);
        return persistence.findCharge(taskId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<RepairViews.Task> contractorMine(ActorContext actor, String view) {
        requireActiveContractor(actor);
        return persistence.findTasksByCurrentContractor(actor.identityId()).stream()
                .map(RepairPersistence.StoredTask::view)
                .filter(task -> switch (view) {
                    case "unstarted" -> task.status() == RepairTaskStatus.PENDING
                            || task.status() == RepairTaskStatus.RETURNED;
                    case "active" -> task.status() == RepairTaskStatus.IN_PROGRESS;
                    case "submitted" -> task.status() == RepairTaskStatus.PENDING_ACCEPTANCE
                            || task.status() == RepairTaskStatus.PENDING_CHARGE
                            || task.status() == RepairTaskStatus.COMPLETED;
                    default -> throw new IllegalArgumentException("Unknown contractor task view.");
                }).toList();
    }

    @Transactional(readOnly = true)
    public RepairViews.Task contractorTask(ActorContext actor, long taskId) {
        requireActiveContractor(actor);
        var task = persistence.findCurrentOwnerTask(taskId, actor.identityId())
                .orElseThrow(() -> notFound(taskId));
        return task.view();
    }

    @Transactional(readOnly = true)
    public List<RepairViews.Labor> contractorTaskLabors(ActorContext actor, long taskId) {
        contractorTask(actor, taskId);
        return persistence.findLaborViewsByTask(taskId).stream()
                .filter(labor -> labor.contractorId() == actor.identityId()).toList();
    }

    @Transactional(readOnly = true)
    public List<RepairViews.Labor> contractorFeeHistory(ActorContext actor) {
        requireActiveContractor(actor);
        authorization.require(actor, ActionPermission.CONTRACTOR_SERVICE_CHARGE_READ, null);
        return persistence.findLaborViewsByContractor(actor.identityId());
    }

    private void requireActiveContractor(ActorContext actor) {
        if (actor == null || actor.identityType() != IdentityType.CONTRACTOR
                || actor.identityId() <= 0) {
            throw new IllegalArgumentException("An authenticated contractor identity is required.");
        }
        authorization.require(actor, ActionPermission.CONTRACTOR_REPAIR_TASK, null);
        var snapshot = contractors.findById(actor.identityId()).orElseThrow(() ->
                new com.bsr.repair.contractor.api.ContractorAccessException(
                        com.bsr.repair.contractor.api.ContractorAccessException.Code.CONTRACTOR_NOT_FOUND,
                        "Contractor does not exist."));
        if (!snapshot.active()) {
            throw new com.bsr.repair.contractor.api.ContractorAccessException(
                    com.bsr.repair.contractor.api.ContractorAccessException.Code.CONTRACTOR_INACTIVE,
                    "Contractor is inactive.");
        }
    }

    private RepairPersistence.StoredTask lockForEmployee(ActorContext actor, long taskId) {
        requireEmployee(actor);
        requirePositive(taskId, "taskId");
        var route = persistence.findTask(taskId).orElseThrow(() -> notFound(taskId));
        authorization.require(actor, ActionPermission.EMPLOYEE_REPAIR_PROCESS, route.serviceStoreId());
        stores.lockForUse(route.serviceStoreId());
        employees.lockForUse(actor.identityId());
        var locked = persistence.lockTask(taskId).orElseThrow(() -> notFound(taskId));
        if (locked.serviceStoreId() != route.serviceStoreId()) {
            throw new RepairOperationException(RepairOperationException.Code.STORE_CONFLICT,
                    "Repair task store changed while processing.");
        }
        return locked;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).withNano(0);
    }

    private RepairPersistence.StoredTask routeForContractor(ActorContext actor, long taskId) {
        requirePositive(taskId, "taskId");
        if (actor == null || actor.identityType() != IdentityType.CONTRACTOR
                || actor.identityId() <= 0) {
            throw new IllegalArgumentException("An authenticated contractor identity is required.");
        }
        authorization.require(actor, ActionPermission.CONTRACTOR_REPAIR_TASK, null);
        var route = persistence.findCurrentOwnerTask(taskId, actor.identityId())
                .orElseThrow(() -> notFound(taskId));
        return route;
    }

    private RepairPersistence.StoredTask lockForContractor(ActorContext actor, long taskId) {
        var locked = persistence.lockTask(taskId).orElseThrow(() -> notFound(taskId));
        authorization.requireContractorOwnership(actor, locked.task().currentContractorId() == null
                ? 0 : locked.task().currentContractorId());
        return locked;
    }

    private static RepairOperationException taskConflict(IllegalStateException cause) {
        return new RepairOperationException(RepairOperationException.Code.TASK_STATE_CONFLICT,
                cause.getMessage());
    }

    private static RepairOperationException notFound(long taskId) {
        return new RepairOperationException(RepairOperationException.Code.TASK_NOT_FOUND,
                "Repair task " + taskId + " does not exist.");
    }

    private static void requireEmployee(ActorContext actor) {
        if (actor == null || actor.identityType() != IdentityType.EMPLOYEE || actor.identityId() <= 0) {
            throw new IllegalArgumentException("An authenticated employee identity is required.");
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank() || value.trim().length() > 500) {
            throw new IllegalArgumentException(field + " must contain 1–500 characters.");
        }
        return value.trim();
    }
}
