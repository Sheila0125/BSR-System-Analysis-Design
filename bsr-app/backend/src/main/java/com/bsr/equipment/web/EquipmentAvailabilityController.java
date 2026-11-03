package com.bsr.equipment.web;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.equipment.api.EquipmentOperations;
import com.bsr.equipment.api.EquipmentSnapshot;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.platform.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentAvailabilityController {

    private final EquipmentOperations equipmentOperations;
    private final AccountAuthentication accountAuthentication;

    public EquipmentAvailabilityController(
            EquipmentOperations equipmentOperations,
            AccountAuthentication accountAuthentication) {
        this.equipmentOperations = equipmentOperations;
        this.accountAuthentication = accountAuthentication;
    }

    @GetMapping("/available")
    List<AvailableEquipmentResponse> available(
            @RequestParam String storeId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String skillLevel,
            Authentication authentication) {
        BsrAccountPrincipal principal = (BsrAccountPrincipal) authentication.getPrincipal();
        ActorContext actor = accountAuthentication.loadActor(principal.accountId());
        return equipmentOperations.selectAvailable(
                actor, positiveId(storeId), optionalCode(category, "category"), optionalCode(skillLevel, "skillLevel"))
                .stream().map(AvailableEquipmentResponse::from).toList();
    }

    private String optionalCode(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String code = value.trim();
        if (code.length() > 40) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    field + " must not exceed 40 characters.");
        }
        return code;
    }

    private long positiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw validation();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw validation();
        }
    }

    private ApiException validation() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "storeId must be a positive decimal identifier.");
    }

    record AvailableEquipmentResponse(
            String equipmentId,
            String inventoryNumber,
            String equipmentName,
            String equipmentCategory,
            String skillLevel,
            String homeStoreId,
            String currentStoreId,
            String status,
            String currentDailyRate) {

        static AvailableEquipmentResponse from(EquipmentSnapshot equipment) {
            return new AvailableEquipmentResponse(
                    Long.toString(equipment.equipmentId()),
                    equipment.inventoryNumber(),
                    equipment.equipmentName(),
                    equipment.equipmentCategory(),
                    equipment.skillLevel(),
                    Long.toString(equipment.homeStoreId()),
                    Long.toString(equipment.currentStoreId()),
                    equipment.status().name(),
                    equipment.currentDailyRate().toPlainString());
        }
    }
}
