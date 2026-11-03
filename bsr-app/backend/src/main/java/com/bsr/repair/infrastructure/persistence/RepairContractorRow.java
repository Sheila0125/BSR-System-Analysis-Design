package com.bsr.repair.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.bsr.repair.application.RepairViews;

@Entity(name = "RepairContractorRow")
@Table(name = "repair_contractor")
public class RepairContractorRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contractor_id", nullable = false)
    Long contractorId;

    @Column(name = "contractor_number", nullable = false, length = 30)
    String contractorNumber;

    @Column(name = "contractor_name", nullable = false, length = 120)
    String contractorName;

    @Column(name = "contact_name", nullable = true, length = 100)
    String contactName;

    @Column(name = "email", nullable = false, length = 255)
    String email;

    @Column(name = "phone", nullable = true, length = 40)
    String phone;

    @Column(name = "business_status", nullable = false, length = 20)
    String businessStatus;

    @Column(name = "postal_address", length = 255)
    String postalAddress;

    protected RepairContractorRow() {
    }

    RepairContractorRow(String number, String name, String contactName, String email,
            String phone, String postalAddress) {
        this.contractorNumber = number;
        update(name, contactName, email, phone);
        updatePostalAddress(postalAddress);
        this.businessStatus = "ACTIVE";
    }

    void update(String name, String contactName, String email, String phone) {
        contractorName = name;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
    }

    void deactivate() {
        businessStatus = "INACTIVE";
    }

    void updatePostalAddress(String value) {
        postalAddress = value == null || value.isBlank() ? null : value.trim();
    }

    RepairViews.Contractor view() {
        return new RepairViews.Contractor(contractorId, contractorNumber,
                contractorName, contactName, email, phone, "ACTIVE".equals(businessStatus), postalAddress);
    }
}
