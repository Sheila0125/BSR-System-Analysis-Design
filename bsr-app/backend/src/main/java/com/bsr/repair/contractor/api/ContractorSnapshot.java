package com.bsr.repair.contractor.api;

public record ContractorSnapshot(
        long contractorId,
        String contractorNumber,
        String contractorName,
        String contactName,
        String email,
        String phone,
        boolean active) {
}
