package com.bsr.scheduling.employee.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bsr.scheduling.employee.api.EmployeeAccessException;
import com.bsr.scheduling.employee.api.EmployeeSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmployeeTest {

    private final Employee activeEmployee = new Employee(
            new EmployeeSnapshot(1L, "E-1", "Test Employee", "employee@test.invalid", null, true));

    @Test
    void acBr15CancelledHistoryDoesNotBlockSameDayShift() {
        assertDoesNotThrow(() -> activeEmployee.validateNoSameDayShift(
                List.of(new SameDayShiftConflict(10L, false))));
    }

    @Test
    void acBr15ActiveSameDayShiftIsRejected() {
        EmployeeAccessException error = assertThrows(
                EmployeeAccessException.class,
                () -> activeEmployee.validateNoSameDayShift(List.of(
                        new SameDayShiftConflict(10L, false),
                        new SameDayShiftConflict(11L, true))));

        assertEquals(EmployeeAccessException.Code.SAME_DAY_SHIFT_CONFLICT, error.code());
    }
}
