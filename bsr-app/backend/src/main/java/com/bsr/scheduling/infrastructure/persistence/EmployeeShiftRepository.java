package com.bsr.scheduling.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface EmployeeShiftRepository extends JpaRepository<EmployeeShiftRow, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EmployeeShiftRow s where s.shiftId = :shiftId")
    Optional<EmployeeShiftRow> findByIdForUpdate(@Param("shiftId") long shiftId);

    List<EmployeeShiftRow> findByEmployeeIdAndShiftDateAndShiftStatusOrderByShiftIdAsc(
            long employeeId, LocalDate shiftDate, String shiftStatus);

    List<EmployeeShiftRow> findByEmployeeIdOrderByShiftDateAscShiftIdAsc(long employeeId);

    List<EmployeeShiftRow> findByStoreIdOrderByShiftDateAscShiftIdAsc(long storeId);

    List<EmployeeShiftRow> findByStoreIdAndShiftDateGreaterThanAndShiftStatusOrderByShiftDateAscShiftIdAsc(
            long storeId, LocalDate businessToday, String shiftStatus);
}
