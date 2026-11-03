package com.bsr.repair.application;

import com.bsr.repair.domain.RepairTask;
import com.bsr.repair.domain.RepairLabor;
import com.bsr.repair.domain.RepairRatePeriod;
import com.bsr.repair.domain.RepairServiceCharge;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RepairPersistence {

    boolean hasUnfinishedTask(long equipmentId);

    Optional<StoredTask> findTask(long taskId);

    Optional<StoredTask> findCurrentOwnerTask(long taskId, long contractorId);

    List<StoredTask> findTasksByStore(long storeId);

    List<StoredTask> findTasksByCurrentContractor(long contractorId);

    Optional<StoredTask> lockTask(long taskId);

    void saveTask(StoredTask task);

    StoredTask insertTask(String number, long equipmentId, long employeeId, long storeId,
            LocalDate requestDate, String faultDescription, RepairTask task);

    Optional<RateMatch> findRate(long contractorId, LocalDate repairDate);

    RepairViews.Labor insertLabor(long taskId, RepairLabor labor);

    int countUnsubmittedLabors(long taskId, long contractorId);

    void markLaborsSubmitted(long taskId, long contractorId, LocalDateTime submittedAt);

    List<RepairLabor> findLabors(long taskId);

    List<RepairViews.Labor> findLaborViewsByTask(long taskId);

    List<RepairViews.Labor> findLaborViewsByContractor(long contractorId);

    StoredTask saveAcceptance(StoredTask task, long employeeId, LocalDateTime acceptedAt);

    RepairViews.Charge createPendingCharge(long taskId, RepairServiceCharge charge);

    Optional<RepairViews.Charge> findCharge(long taskId);

    RepairViews.Charge confirmCharge(long taskId, long employeeId, LocalDateTime confirmedAt);

    record RateMatch(long id, RepairRatePeriod period) {
    }

    record StoredTask(long id, String number, long equipmentId, long serviceStoreId,
            LocalDate requestDate, String faultDescription, RepairTask task,
            LocalDateTime acceptedAt) {

        public RepairViews.Task view() {
            return new RepairViews.Task(id, number, equipmentId, serviceStoreId,
                    task.currentContractorId(), requestDate, faultDescription,
                    task.status(), task.acceptanceResult(), acceptedAt);
        }
    }
}
