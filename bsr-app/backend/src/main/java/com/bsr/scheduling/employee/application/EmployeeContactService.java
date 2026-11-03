package com.bsr.scheduling.employee.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.IdentityType;
import com.bsr.platform.error.ApiException;
import com.bsr.scheduling.employee.api.EmployeeAccess;
import com.bsr.storetransfer.store.api.StoreAccess;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.LongStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeContactService {
    private final EmployeeContactPersistence contacts;
    private final Authorization authorization;
    private final StoreAccess stores;
    private final EmployeeAccess employees;
    private final AuditAppender audit;
    private final Clock clock;
    public EmployeeContactService(EmployeeContactPersistence contacts, Authorization authorization,
            StoreAccess stores, EmployeeAccess employees, AuditAppender audit, Clock clock) {
        this.contacts=contacts; this.authorization=authorization; this.stores=stores;
        this.employees=employees; this.audit=audit; this.clock=clock;
    }

    @Transactional(readOnly=true)
    public EmployeeContactPersistence.Contact read(ActorContext actor,long storeId,long employeeId) {
        requireManager(actor,storeId);
        return scoped(storeId,employeeId);
    }

    @Transactional(readOnly=true)
    public java.util.List<EmployeeContactPersistence.Choice> choices(ActorContext actor,long storeId) {
        requireManager(actor,storeId);
        return contacts.choicesAtStore(storeId,LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public EmployeeContactPersistence.Contact update(ActorContext actor,long storeId,long employeeId,String address,String note) {
        requireManager(actor,storeId);
        scoped(storeId,employeeId);
        validate(address,255); validate(note,500);
        stores.lockForUse(storeId);
        LongStream.of(actor.identityId(),employeeId).distinct().sorted().forEach(employees::lockForUse);
        var old=scoped(storeId,employeeId);
        contacts.updateSupplement(employeeId,normalize(address,old.address()),normalize(note,old.note()));
        audit.appendSuccess(actor,"EMPLOYEE",employeeId,"EMPLOYEE_CONTACT_UPDATED","storeId="+storeId);
        return scoped(storeId,employeeId);
    }

    private void requireManager(ActorContext actor,long storeId) {
        authorization.require(actor,ActionPermission.MANAGER_STORE_AND_SCHEDULING,storeId);
        if(actor.identityType()!=IdentityType.EMPLOYEE) throw new ApiException(HttpStatus.FORBIDDEN,"ACTION_NOT_AUTHORIZED","A manager employee is required.");
    }
    private EmployeeContactPersistence.Contact scoped(long storeId,long employeeId) {
        return contacts.findAtStore(employeeId,storeId,LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"EMPLOYEE_NOT_FOUND","Employee not found in this store scope."));
    }
    private static void validate(String value,int max) {
        if(value!=null && value.trim().length()>max) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Employee contact field exceeds its maximum length.");
    }
    private static String normalize(String value,String old) { return value==null?old:value.isBlank()?null:value.trim(); }
}
