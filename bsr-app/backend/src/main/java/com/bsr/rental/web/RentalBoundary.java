package com.bsr.rental.web;

import com.bsr.equipment.api.EquipmentOperationException;
import com.bsr.platform.error.ApiException;
import com.bsr.scheduling.employee.api.EmployeeAccessException;
import com.bsr.storetransfer.store.api.StoreAccessException;
import java.math.BigDecimal;
import java.util.function.Supplier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;

final class RentalBoundary {
    private RentalBoundary() { }
    static <T> T call(Supplier<T> operation) {
        try { return operation.get(); }
        catch(IllegalArgumentException ex) { throw failure(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED",ex.getMessage()); }
        catch(java.time.DateTimeException ex) { throw failure(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Expected an ISO UTC date/time."); }
        catch(IllegalStateException ex) { throw failure(HttpStatus.CONFLICT,"RENTAL_STATE_CONFLICT",ex.getMessage()); }
        catch(EquipmentOperationException ex) { throw failure(HttpStatus.CONFLICT,ex.code().name(),ex.getMessage()); }
        catch(StoreAccessException ex) { throw failure(HttpStatus.CONFLICT,ex.code().name(),ex.getMessage()); }
        catch(EmployeeAccessException ex) { throw failure(HttpStatus.CONFLICT,ex.code().name(),ex.getMessage()); }
        catch(PessimisticLockingFailureException ex) { throw failure(HttpStatus.CONFLICT,"RENTAL_LOCK_CONFLICT","Concurrent operation; reload before trying again."); }
    }
    static long id(String value) {
        if(value==null || !value.matches("[1-9][0-9]{0,18}")) throw failure(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Expected a positive decimal identifier string.");
        return call(()->Long.parseLong(value));
    }
    static <E extends Enum<E>> E enumValue(Class<E> type,String value) {
        if(value==null) throw failure(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Required action type or status is missing.");
        return Enum.valueOf(type,value);
    }
    static <T> T required(T value) {
        if(value==null) throw failure(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","List items must not be null.");
        return value;
    }
    static BigDecimal money(String value) {
        if(value==null || !value.matches("(?:0|[1-9][0-9]{0,9})\\.[0-9]{2}"))
            throw failure(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Expected a two-decimal money string.");
        return new BigDecimal(value);
    }
    private static ApiException failure(HttpStatus status,String code,String message) { return new ApiException(status,code,message); }
}
