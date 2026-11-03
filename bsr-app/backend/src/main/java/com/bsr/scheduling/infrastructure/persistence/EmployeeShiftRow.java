package com.bsr.scheduling.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "EmployeeShiftRow")
@Table(name = "employee_shift")
public class EmployeeShiftRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shift_id", nullable = false)
    Long shiftId;

    @Column(name = "shift_number", nullable = false, length = 30)
    String shiftNumber;

    @Column(name = "shift_date", nullable = false)
    LocalDate shiftDate;

    @Column(name = "store_id", nullable = false)
    Long storeId;

    @Column(name = "employee_id", nullable = false)
    Long employeeId;

    @Column(name = "start_time", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_TIME)
    LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_TIME)
    LocalTime endTime;

    @Column(name = "shift_status", nullable = false, length = 20)
    String shiftStatus;

    @Column(name = "changed_by_employee_id", nullable = false)
    Long changedByEmployeeId;

    @Column(name = "changed_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    LocalDateTime changedAt;

    @Column(name = "cancellation_reason", nullable = true, length = 255)
    String cancellationReason;

    protected EmployeeShiftRow() {
    }
}
