package com.bsr.repair.web;

import com.bsr.equipment.api.EquipmentOperationException;
import com.bsr.platform.error.ApiException;
import com.bsr.repair.application.RepairOperationException;
import com.bsr.repair.contractor.api.ContractorAccessException;
import com.bsr.scheduling.employee.api.EmployeeAccessException;
import com.bsr.storetransfer.store.api.StoreAccessException;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;

final class RepairBoundary {

    private RepairBoundary() {
    }

    static <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (RepairOperationException exception) {
            HttpStatus status = exception.code() == RepairOperationException.Code.TASK_NOT_FOUND
                    ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
            throw new ApiException(status, exception.code().name(), exception.getMessage());
        } catch (ContractorAccessException exception) {
            HttpStatus status = exception.code() == ContractorAccessException.Code.CONTRACTOR_NOT_FOUND
                    ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
            throw new ApiException(status, exception.code().name(), exception.getMessage());
        } catch (EquipmentOperationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.code().name(), exception.getMessage());
        } catch (StoreAccessException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.code().name(), exception.getMessage());
        } catch (EmployeeAccessException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.code().name(), exception.getMessage());
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "REPAIR_CONCURRENT_CONFLICT",
                    "Repair data changed concurrently; reload and try again.");
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "REPAIR_STATE_CONFLICT", exception.getMessage());
        }
    }

    static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "Expected a positive decimal identifier string.");
        }
        return call(() -> Long.parseLong(value));
    }

    static LocalDate date(String value) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "An ISO local date is required.");
        }
        return call(() -> LocalDate.parse(value));
    }

    static BigDecimal decimal(String value) {
        if (value == null || !value.matches("(?:0|[1-9][0-9]{0,9})\\.[0-9]{2}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "Expected a nonnegative two-decimal amount string.");
        }
        return new BigDecimal(value);
    }
}
