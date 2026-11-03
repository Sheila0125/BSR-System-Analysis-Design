package com.bsr.equipment.application;

import com.bsr.equipment.domain.Equipment;
import com.bsr.equipment.domain.EquipmentInspection;
import java.util.List;

public interface EquipmentPersistence {

    boolean rejectedAdditionBelongsToCustomer(long changeId,long contractId,long customerId,long equipmentId,long storeId);

    List<Equipment> findAvailable(long storeId, String category, String skillLevel);

    List<Equipment> lockAllInAscendingOrder(List<Long> equipmentIds);

    void saveAll(List<Equipment> equipment);

    boolean rentalContractReferencesEquipment(long rentalContractId, long equipmentId);

    boolean rentalItemReferencesEquipment(long rentalItemId, long equipmentId);

    boolean repairTaskReferencesEquipment(long repairTaskId, long equipmentId);

    boolean transferReferencesEquipment(long transferId, long equipmentId);

    long appendInspection(EquipmentInspection inspection);
}
