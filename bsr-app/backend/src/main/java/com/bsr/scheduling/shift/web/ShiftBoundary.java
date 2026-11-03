package com.bsr.scheduling.shift.web;

import com.bsr.platform.error.ApiException;
import com.bsr.scheduling.employee.api.EmployeeAccessException;
import com.bsr.storetransfer.store.api.StoreAccessException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

final class ShiftBoundary {
    private ShiftBoundary() {
    }

    static <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (EmployeeAccessException exception) {
            HttpStatus status = exception.code() == EmployeeAccessException.Code.EMPLOYEE_NOT_FOUND
                    ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
            throw new ApiException(status, exception.code().name(), exception.getMessage());
        } catch (StoreAccessException exception) {
            HttpStatus status = exception.code() == StoreAccessException.Code.STORE_NOT_FOUND
                    ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
            throw new ApiException(status, exception.code().name(), exception.getMessage());
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "SHIFT_CONCURRENT_CONFLICT",
                    "Shift data changed concurrently; reload and try again.");
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw bad(exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "SHIFT_STATE_CONFLICT", exception.getMessage());
        }
    }

    static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw bad("Expected a positive decimal identifier string.");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw bad("Identifier is outside the supported range.");
        }
    }

    static long id(JsonNode node, String field) {
        return id(text(node, field));
    }

    static String text(JsonNode node, String field) {
        if (node == null || !node.isTextual()) {
            throw bad(field + " must be a string.");
        }
        return node.textValue();
    }

    static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw bad(field + " must be YYYY-MM-DD.");
        }
        return LocalDate.parse(value);
    }

    static LocalTime time(JsonNode node, String field) {
        String value = text(node, field);
        if (!value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) {
            throw bad(field + " must be HH:mm.");
        }
        return LocalTime.parse(value);
    }

    static void allowed(JsonNode body, String... names) {
        if (body == null || !body.isObject()) {
            throw bad("Expected a JSON object.");
        }
        Set<String> allowed = new HashSet<>(Arrays.asList(names));
        body.propertyNames().forEach(name -> {
            if (!allowed.contains(name)) {
                throw bad("Unexpected request field: " + name);
            }
        });
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }
}
