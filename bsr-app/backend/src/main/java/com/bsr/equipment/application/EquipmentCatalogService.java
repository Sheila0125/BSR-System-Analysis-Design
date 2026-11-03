package com.bsr.equipment.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.equipment.application.EquipmentCatalogPersistence.Entry;
import com.bsr.equipment.application.EquipmentCatalogPersistence.Metadata;
import com.bsr.equipment.domain.EquipmentStatus;
import com.bsr.equipment.domain.EquipmentTariff;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.identity.api.RoleCode;
import com.bsr.platform.error.ApiException;
import com.bsr.storetransfer.store.api.StoreAccess;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentCatalogService {
    private final EquipmentCatalogPersistence persistence;
    private final Authorization authorization;
    private final StoreAccess stores;
    private final AuditAppender audit;
    public EquipmentCatalogService(EquipmentCatalogPersistence persistence, Authorization authorization,
                                   StoreAccess stores, AuditAppender audit) {
        this.persistence=persistence; this.authorization=authorization; this.stores=stores; this.audit=audit;
    }

    @Transactional(readOnly=true)
    public List<Entry> find(ActorContext actor,long storeId,String status,String inventory,String category,String skill) {
        boolean manager=actor.activeGrants().stream().anyMatch(g->g.role()==RoleCode.MANAGER
                && Long.valueOf(storeId).equals(g.storeId()));
        authorization.require(actor,manager?ActionPermission.MANAGER_EQUIPMENT_OVERVIEW:
                ActionPermission.EMPLOYEE_EQUIPMENT_QUERY,storeId);
        String state=optional(status,20);
        if(state!=null) { try { EquipmentStatus.valueOf(state); } catch(IllegalArgumentException e) { throw invalid("status"); } }
        return persistence.find(storeId,state,optional(inventory,40),optional(category,40),optional(skill,20));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Entry create(ActorContext actor,long storeId,String inventory,String category,Metadata metadata) {
        requireWrite(actor,storeId);
        String number=required(inventory,40); String type=required(category,40);
        Metadata valid=validate(metadata,type);
        try {
            Entry result=persistence.insert(storeId,number,type,valid);
            audit.appendSuccess(actor,"EQUIPMENT",result.id(),"EQUIPMENT_ARCHIVE_CREATED","storeId="+storeId);
            return result;
        } catch(DuplicateKeyException e) { throw conflict("INVENTORY_NUMBER_EXISTS","Inventory number already exists."); }
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Entry update(ActorContext actor,long storeId,long id,long revision,Metadata metadata) {
        requireWrite(actor,storeId);
        Entry existing=persistence.lock(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"EQUIPMENT_NOT_FOUND","Equipment not found."));
        if(existing.currentStoreId()!=storeId) throw new ApiException(HttpStatus.FORBIDDEN,"STORE_SCOPE_DENIED","Equipment is outside the selected store.");
        if(existing.revision()!=revision) throw conflict("EQUIPMENT_ARCHIVE_STALE","Equipment archive changed. Reload before saving.");
        Entry result=persistence.update(existing,validate(metadata,existing.category()));
        audit.appendSuccess(actor,"EQUIPMENT",id,"EQUIPMENT_ARCHIVE_UPDATED","storeId="+storeId+";revision="+result.revision());
        return result;
    }

    private void requireWrite(ActorContext actor,long storeId) {
        authorization.require(actor,ActionPermission.MANAGER_EQUIPMENT_MAINTAIN,storeId);
        if(!stores.lockForUse(storeId).active()) throw conflict("STORE_INACTIVE","Store is inactive.");
    }

    private Metadata validate(Metadata m,String category) {
        BigDecimal rate=m.currentDailyRate();
        if(rate==null) rate=EquipmentTariff.dailyRates().get(category);
        if(rate==null) throw invalid("currentDailyRate");
        return new Metadata(required(m.name(),120),required(m.skillLevel(),20),optional(m.description(),1000),
                m.purchaseDate(),money(m.purchasePrice()),money(m.retailPrice()),money(rate));
    }

    public static String optional(String value,int max) {
        if(value==null||value.isBlank()) return null;
        String text=value.trim(); if(text.length()>max) throw invalid("text length"); return text;
    }
    private static String required(String value,int max) { String text=optional(value,max); if(text==null) throw invalid("required text"); return text; }
    public static BigDecimal money(BigDecimal value) {
        if(value==null) return null;
        if(value.signum()<0||value.compareTo(new BigDecimal("9999999999.99"))>0) throw invalid("money");
        try { return value.setScale(2,RoundingMode.UNNECESSARY); } catch(ArithmeticException e) { throw invalid("money precision"); }
    }
    public static LocalDate date(String value) {
        if(value==null||value.isBlank()) return null;
        try { return LocalDate.parse(value); } catch(DateTimeParseException e) { throw invalid("purchaseDate"); }
    }
    public static long identifier(String value,boolean allowZero) {
        if(value==null||!value.matches(allowZero?"(?:0|[1-9][0-9]{0,18})":"[1-9][0-9]{0,18}")) throw invalid("identifier");
        try { return Long.parseLong(value); } catch(NumberFormatException e) { throw invalid("identifier"); }
    }
    private static ApiException invalid(String field) { return new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Invalid "+field+"."); }
    private static ApiException conflict(String code,String message) { return new ApiException(HttpStatus.CONFLICT,code,message); }
}
