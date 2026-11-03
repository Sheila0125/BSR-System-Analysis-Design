package com.bsr.storetransfer.web;

import com.bsr.equipment.api.EquipmentOperationException;
import com.bsr.platform.error.ApiException;
import com.bsr.scheduling.employee.api.EmployeeAccessException;
import com.bsr.storetransfer.store.api.StoreAccessException;
import tools.jackson.databind.JsonNode;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;

final class StoreTransferBoundary {
    private StoreTransferBoundary() {
    }

    static <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (EquipmentOperationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.code().name(), exception.getMessage());
        } catch (StoreAccessException exception) {
            HttpStatus status = exception.code() == StoreAccessException.Code.STORE_NOT_FOUND
                    ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
            throw new ApiException(status, exception.code().name(), exception.getMessage());
        } catch (EmployeeAccessException exception) {
            throw new ApiException(HttpStatus.CONFLICT, exception.code().name(), exception.getMessage());
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "STORE_TRANSFER_CONCURRENT_CONFLICT",
                    "Store or transfer data changed concurrently; reload and try again.");
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "TRANSFER_STATE_CONFLICT", exception.getMessage());
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
        if (node == null || !node.isTextual()) {
            throw bad(field + " must be a decimal string.");
        }
        return id(node.textValue());
    }

    static List<Long> ids(JsonNode node, String field) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw bad(field + " must be a nonempty array of decimal strings.");
        }
        List<Long> values = new ArrayList<>();
        node.forEach(item -> values.add(id(item, field)));
        return List.copyOf(values);
    }

    static String text(JsonNode node, String field, boolean optional) {
        if (node == null || node.isNull()) {
            if (optional) return null;
            throw bad(field + " is required.");
        }
        if (!node.isTextual()) {
            throw bad(field + " must be a string.");
        }
        return node.textValue();
    }

    static LocalTime time(JsonNode node, String field) {
        String value = text(node, field, false);
        try {
            return LocalTime.parse(value);
        } catch (DateTimeException exception) {
            throw bad(field + " must be an ISO local time.");
        }
    }

    static <E extends Enum<E>> E value(Class<E> type, JsonNode node, String field) {
        String value = text(node, field, false);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw bad(field + " has an unsupported value.");
        }
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
