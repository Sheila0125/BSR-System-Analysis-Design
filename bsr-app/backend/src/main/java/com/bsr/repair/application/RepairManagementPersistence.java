package com.bsr.repair.application;

import com.bsr.repair.domain.RepairRatePeriod;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RepairManagementPersistence {

    RepairViews.Contractor insertContractor(String number, String name, String contactName,
            String email, String phone, String postalAddress);

    RepairViews.Contractor updateContractor(long contractorId, String name, String contactName,
            String email, String phone, String postalAddress);

    RepairViews.Contractor deactivateContractor(long contractorId);

    Optional<RepairViews.Contractor> findContractor(long contractorId);

    List<RepairViews.Contractor> listContractors();

    boolean hasUnfinishedAssignment(long contractorId);

    List<RepairViews.Rate> listRates(long contractorId);

    RepairViews.Rate insertRate(RepairRatePeriod period, LocalDateTime createdAt);

    List<Long> backfillUnratedLabor(long rateId, RepairRatePeriod period);
}
