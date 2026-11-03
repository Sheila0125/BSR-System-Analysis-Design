package com.bsr.repair.infrastructure.persistence;

import com.bsr.repair.application.RepairManagementPersistence;
import com.bsr.repair.application.RepairViews;
import com.bsr.repair.domain.RepairRatePeriod;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRepairManagementPersistence implements RepairManagementPersistence {

    private final RepairContractorRepository contractors;
    private final RepairRateRepository rates;
    private final RepairLaborRepository labors;
    private final RepairTaskRepository tasks;

    public JpaRepairManagementPersistence(RepairContractorRepository contractors,
            RepairRateRepository rates, RepairLaborRepository labors,
            RepairTaskRepository tasks) {
        this.contractors = contractors;
        this.rates = rates;
        this.labors = labors;
        this.tasks = tasks;
    }

    @Override
    public RepairViews.Contractor insertContractor(String number, String name,
            String contactName, String email, String phone, String postalAddress) {
        return contractors.saveAndFlush(new RepairContractorRow(number, name,
                contactName, email, phone, postalAddress)).view();
    }

    @Override
    public RepairViews.Contractor updateContractor(long contractorId, String name,
            String contactName, String email, String phone, String postalAddress) {
        var row = contractors.findById(contractorId).orElseThrow();
        row.update(name, contactName, email, phone);
        if (postalAddress != null) row.updatePostalAddress(postalAddress);
        contractors.flush();
        return row.view();
    }

    @Override
    public RepairViews.Contractor deactivateContractor(long contractorId) {
        var row = contractors.findById(contractorId).orElseThrow();
        row.deactivate();
        contractors.flush();
        return row.view();
    }

    @Override
    public Optional<RepairViews.Contractor> findContractor(long contractorId) {
        return contractors.findById(contractorId).map(RepairContractorRow::view);
    }

    @Override
    public List<RepairViews.Contractor> listContractors() {
        return contractors.findAll().stream().map(RepairContractorRow::view).toList();
    }

    @Override
    public boolean hasUnfinishedAssignment(long contractorId) {
        return tasks.hasUnfinishedAssignment(contractorId);
    }

    @Override
    public List<RepairViews.Rate> listRates(long contractorId) {
        return rates.findByContractorIdOrderByEffectiveFromAsc(contractorId).stream()
                .map(RepairRateRow::view).toList();
    }

    @Override
    public RepairViews.Rate insertRate(RepairRatePeriod period, LocalDateTime createdAt) {
        return rates.saveAndFlush(new RepairRateRow(period, createdAt)).view();
    }

    @Override
    public List<Long> backfillUnratedLabor(long rateId, RepairRatePeriod period) {
        var rows = labors.unratedInPeriod(period.contractorId(),
                period.effectiveFrom(), period.effectiveTo());
        rows.forEach(row -> row.applyRate(rateId, period));
        labors.flush();
        return rows.stream().map(RepairLaborRow::taskId).distinct().sorted().toList();
    }
}
