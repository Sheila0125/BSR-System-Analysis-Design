package com.bsr.scheduling.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "EmployeeRow")
@Table(name = "employee")
public class EmployeeRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_id", nullable = false)
    Long employeeId;

    @Column(name = "employee_number", nullable = false, length = 30)
    String employeeNumber;

    @Column(name = "full_name", nullable = false, length = 100)
    String fullName;

    @Column(name = "email", nullable = false, length = 255)
    String email;

    @Column(name = "phone", nullable = true, length = 40)
    String phone;

    @Column(name = "business_status", nullable = false, length = 20)
    String businessStatus;

    @Column(name = "address", length = 255)
    String address;

    @Column(name = "note", length = 500)
    String note;

    protected EmployeeRow() {
    }
}
