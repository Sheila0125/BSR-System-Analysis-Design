package com.bsr.repair.infrastructure.persistence;

import com.bsr.repair.application.RepairPersistence;
import com.bsr.repair.domain.RepairTask;
import com.bsr.repair.domain.RepairLabor;
import com.bsr.repair.domain.RepairRatePeriod;
import com.bsr.repair.domain.RepairServiceCharge;
import com.bsr.repair.application.RepairViews;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRepairPersistence implements RepairPersistence {

    private final RepairTaskRepository tasks;
    private final RepairLaborRepository labors;
    private final RepairRateRepository rates;
    private final RepairServiceChargeRepository charges;
    private final EntityManager entityManager;

    public JpaRepairPersistence(RepairTaskRepository tasks, RepairLaborRepository labors,
            RepairRateRepository rates, RepairServiceChargeRepository charges,
            EntityManager entityManager) {
        this.tasks = tasks;
        this.labors = labors;
        this.rates = rates;
        this.charges = charges;
        this.entityManager = entityManager;
    }

    @Override
    public boolean hasUnfinishedTask(long equipmentId) {
        return tasks.hasUnfinishedTask(equipmentId);
    }

    @Override
    public Optional<StoredTask> findTask(long taskId) {
        return tasks.findById(taskId).map(RepairTaskRow::snapshot);
    }

    @Override
    public Optional<StoredTask> findCurrentOwnerTask(long taskId, long contractorId) {
        return tasks.findByRepairTaskIdAndCurrentContractorId(taskId, contractorId)
                .map(RepairTaskRow::snapshot);
    }

    @Override
    public List<StoredTask> findTasksByStore(long storeId) {
        return tasks.findByServiceStoreIdOrderByRepairTaskIdDesc(storeId).stream()
                .map(RepairTaskRow::snapshot).toList();
    }

    @Override
    public List<StoredTask> findTasksByCurrentContractor(long contractorId) {
        return tasks.findByCurrentContractorIdOrderByRepairTaskIdDesc(contractorId).stream()
                .map(RepairTaskRow::snapshot).toList();
    }

    @Override
    public Optional<StoredTask> lockTask(long taskId) {
        return tasks.findByIdForUpdate(taskId).map(row -> {
            entityManager.refresh(row, LockModeType.PESSIMISTIC_WRITE);
            return row.snapshot();
        });
    }

    @Override
    public void saveTask(StoredTask task) {
        var row = tasks.findById(task.id()).orElseThrow();
        row.apply(task.task());
        tasks.flush();
    }

    @Override
    public StoredTask insertTask(String number, long equipmentId, long employeeId, long storeId,
            LocalDate requestDate, String faultDescription, RepairTask task) {
        var saved = tasks.saveAndFlush(new RepairTaskRow(number, equipmentId, employeeId, storeId,
                requestDate, faultDescription, task));
        return saved.snapshot();
    }

    @Override
    public Optional<RateMatch> findRate(long contractorId, LocalDate repairDate) {
        var matches = rates.matching(contractorId, repairDate);
        if (matches.size() > 1) {
            throw new IllegalStateException("Overlapping historical repair rates exist.");
        }
        return matches.stream().findFirst().map(row -> new RateMatch(row.repairRateId,
                new RepairRatePeriod(row.contractorId, row.effectiveFrom, row.effectiveTo,
                        row.hourlyRate)));
    }

    @Override
    public RepairViews.Labor insertLabor(long taskId, RepairLabor labor) {
        return labors.saveAndFlush(new RepairLaborRow(taskId, labor)).view();
    }

    @Override
    public int countUnsubmittedLabors(long taskId, long contractorId) {
        return Math.toIntExact(labors.countByRepairTaskIdAndContractorIdAndSubmittedAtIsNull(
                taskId, contractorId));
    }

    @Override
    public void markLaborsSubmitted(long taskId, long contractorId, LocalDateTime submittedAt) {
        labors.markSubmitted(taskId, contractorId, submittedAt);
    }

    @Override
    public List<RepairLabor> findLabors(long taskId) {
        return labors.findByRepairTaskIdOrderByRepairLaborId(taskId).stream()
                .map(RepairLaborRow::domain).toList();
    }

    @Override
    public List<RepairViews.Labor> findLaborViewsByTask(long taskId) {
        return labors.findByRepairTaskIdOrderByRepairLaborId(taskId).stream()
                .map(RepairLaborRow::view).toList();
    }

    @Override
    public List<RepairViews.Labor> findLaborViewsByContractor(long contractorId) {
        return labors.findByContractorIdOrderByRepairLaborIdDesc(contractorId).stream()
                .map(RepairLaborRow::view).toList();
    }

    @Override
    public StoredTask saveAcceptance(StoredTask task, long employeeId, LocalDateTime acceptedAt) {
        var row = tasks.findById(task.id()).orElseThrow();
        row.apply(task.task());
        row.recordAcceptance(employeeId, acceptedAt);
        tasks.flush();
        return row.snapshot();
    }

    @Override
    public RepairViews.Charge createPendingCharge(long taskId, RepairServiceCharge charge) {
        if (charges.findByRepairTaskId(taskId).isPresent()) {
            throw new IllegalStateException("Repair service charge already exists.");
        }
        return charges.saveAndFlush(new RepairServiceChargeRow(taskId, charge)).view();
    }

    @Override
    public Optional<RepairViews.Charge> findCharge(long taskId) {
        return charges.findByRepairTaskId(taskId).map(RepairServiceChargeRow::view);
    }

    @Override
    public RepairViews.Charge confirmCharge(long taskId, long employeeId, LocalDateTime confirmedAt) {
        var row = charges.findByRepairTaskId(taskId).orElseThrow();
        row.confirm(employeeId, confirmedAt);
        charges.flush();
        return row.view();
    }
}
