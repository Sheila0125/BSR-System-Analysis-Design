package com.bsr.scheduling.employee.application;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface EmployeeContactPersistence {
    record Contact(long employeeId, String employeeNumber, String fullName, String email,
            String phone, String address, String note) { }
    Optional<Contact> findAtStore(long employeeId, long storeId, LocalDateTime now);
    record Choice(long employeeId, String employeeNumber, String fullName) { }
    List<Choice> choicesAtStore(long storeId, LocalDateTime now);
    void updateSupplement(long employeeId, String address, String note);
}
