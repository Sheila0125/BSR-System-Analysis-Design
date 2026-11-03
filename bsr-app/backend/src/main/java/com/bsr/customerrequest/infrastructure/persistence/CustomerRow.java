package com.bsr.customerrequest.infrastructure.persistence;

import com.bsr.customerrequest.domain.CustomerProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity(name = "CustomerRow")
@Table(name = "customer")
public class CustomerRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id", nullable = false)
    Long customerId;

    @Column(name = "customer_number", nullable = false, length = 30)
    String customerNumber;

    @Column(name = "full_name", nullable = false, length = 100)
    String fullName;

    @Column(name = "email", nullable = false, length = 255)
    String email;

    @Column(name = "phone", nullable = false, length = 40)
    String phone;

    @Column(name = "address", nullable = true, length = 255)
    String address;

    @Column(name = "birth_year", nullable = true)
    Short birthYear;

    @Column(name = "height_cm", nullable = true, precision = 5, scale = 2)
    BigDecimal heightCm;

    @Column(name = "weight_kg", nullable = true, precision = 5, scale = 2)
    BigDecimal weightKg;

    @Column(name = "boot_size", nullable = true, precision = 4, scale = 1)
    BigDecimal bootSize;

    @Column(name = "height_estimated", nullable = false)
    Boolean heightEstimated;

    @Column(name = "weight_estimated", nullable = false)
    Boolean weightEstimated;

    @Column(name = "boot_size_estimated", nullable = false)
    Boolean bootSizeEstimated;

    protected CustomerRow() {
    }

    void applyProfile(CustomerProfile profile) {
        fullName = profile.fullName();
        email = profile.email();
        phone = profile.phone();
        address = profile.address();
        birthYear = profile.birthYear();
        heightCm = profile.heightCm();
        weightKg = profile.weightKg();
        bootSize = profile.bootSize();
        heightEstimated = profile.heightEstimated();
        weightEstimated = profile.weightEstimated();
        bootSizeEstimated = profile.bootSizeEstimated();
    }

    Long customerId() {
        return customerId;
    }

    String customerNumber() {
        return customerNumber;
    }

    String fullName() {
        return fullName;
    }

    String email() {
        return email;
    }

    String phone() {
        return phone;
    }

    String address() {
        return address;
    }

    Short birthYear() {
        return birthYear;
    }

    BigDecimal heightCm() {
        return heightCm;
    }

    BigDecimal weightKg() {
        return weightKg;
    }

    BigDecimal bootSize() {
        return bootSize;
    }

    Boolean heightEstimated() {
        return heightEstimated;
    }

    Boolean weightEstimated() {
        return weightEstimated;
    }

    Boolean bootSizeEstimated() {
        return bootSizeEstimated;
    }
}
