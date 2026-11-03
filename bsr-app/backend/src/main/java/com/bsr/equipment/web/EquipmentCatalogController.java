package com.bsr.equipment.web;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.equipment.application.EquipmentCatalogPersistence.Entry;
import com.bsr.equipment.application.EquipmentCatalogPersistence.Metadata;
import com.bsr.equipment.application.EquipmentCatalogService;
import com.bsr.equipment.domain.EquipmentTariff;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EquipmentCatalogController {
    private final EquipmentCatalogService service;
    private final AccountAuthentication accounts;
    public EquipmentCatalogController(EquipmentCatalogService service,AccountAuthentication accounts) {
        this.service=service; this.accounts=accounts;
    }
    private ActorContext actor(Authentication authentication) { return accounts.loadActor(((BsrAccountPrincipal)authentication.getPrincipal()).accountId()); }
    @GetMapping("/api/pricing") Map<String,Object> pricing() {
        Map<String,String> rates=new LinkedHashMap<>();
        EquipmentTariff.dailyRates().forEach((key,value)->rates.put(key,value.toPlainString()));
        return Map.of("currency","NOK","dailyRates",rates);
    }
    @GetMapping("/api/equipment/catalog") List<CatalogResponse> find(Authentication authentication,
            @RequestParam String storeId,@RequestParam(required=false) String status,
            @RequestParam(required=false) String inventoryNumber,@RequestParam(required=false) String category,
            @RequestParam(required=false) String skillLevel) {
        return service.find(actor(authentication),EquipmentCatalogService.identifier(storeId,false),status,inventoryNumber,category,skillLevel)
                .stream().map(CatalogResponse::from).toList();
    }
    @PostMapping("/api/equipment/catalog") @ResponseStatus(HttpStatus.CREATED)
    CatalogResponse create(Authentication authentication,@RequestBody CatalogInput input) {
        return CatalogResponse.from(service.create(actor(authentication),EquipmentCatalogService.identifier(input.storeId(),false),
                input.inventoryNumber(),input.equipmentCategory(),input.metadata()));
    }
    @PostMapping("/api/equipment/catalog/{id}/update")
    CatalogResponse update(Authentication authentication,@PathVariable String id,@RequestBody CatalogInput input) {
        return CatalogResponse.from(service.update(actor(authentication),EquipmentCatalogService.identifier(input.storeId(),false),
                EquipmentCatalogService.identifier(id,false),EquipmentCatalogService.identifier(input.expectedRevision(),true),input.metadata()));
    }
    record CatalogInput(String storeId,String inventoryNumber,String equipmentName,String equipmentCategory,String skillLevel,
                        String description,String purchaseDate,BigDecimal purchasePrice,BigDecimal retailPrice,
                        BigDecimal currentDailyRate,String expectedRevision) {
        Metadata metadata() { return new Metadata(equipmentName,skillLevel,description,EquipmentCatalogService.date(purchaseDate),purchasePrice,retailPrice,currentDailyRate); }
    }
    record CatalogResponse(String equipmentId,String inventoryNumber,String equipmentName,String equipmentCategory,String skillLevel,
                           String description,String purchaseDate,String purchasePrice,String retailPrice,String currentDailyRate,
                           String homeStoreId,String currentStoreId,String status,String revision) {
        static CatalogResponse from(Entry e) {
            Metadata m=e.metadata();
            return new CatalogResponse(Long.toString(e.id()),e.inventoryNumber(),m.name(),e.category(),m.skillLevel(),m.description(),
                    m.purchaseDate()==null?null:m.purchaseDate().toString(),decimal(m.purchasePrice()),decimal(m.retailPrice()),decimal(m.currentDailyRate()),
                    Long.toString(e.homeStoreId()),Long.toString(e.currentStoreId()),e.status(),Long.toString(e.revision()));
        }
        private static String decimal(BigDecimal money) { return money==null?null:money.toPlainString(); }
    }
}
