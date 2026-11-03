package com.bsr.equipment.infrastructure.persistence;

import com.bsr.equipment.application.EquipmentPersistence;
import com.bsr.equipment.domain.Equipment;
import com.bsr.equipment.domain.EquipmentInspection;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEquipmentPersistence implements EquipmentPersistence {

    @Override
    public boolean rejectedAdditionBelongsToCustomer(long changeId,long contractId,long customerId,long equipmentId,long storeId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM rental_change cg
                JOIN rental_contract c ON c.contract_id=cg.contract_id
                JOIN rental_change_item e ON e.rental_change_id=cg.rental_change_id
                JOIN rental_item i ON i.rental_item_id=e.rental_item_id AND i.contract_id=c.contract_id
                WHERE cg.rental_change_id=? AND c.contract_id=? AND c.customer_id=?
                  AND i.equipment_id=? AND cg.requested_store_id=? AND cg.change_status='REJECTED'
                  AND e.effect_code IN ('ADD','REPLACE_IN') AND i.item_status='PENDING_ISSUE'
                """,Integer.class,changeId,contractId,customerId,equipmentId,storeId)==1;
    }

    private final EquipmentRepository equipmentRepository;
    private final EquipmentInspectionRepository inspectionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public JpaEquipmentPersistence(
            EquipmentRepository equipmentRepository,
            EquipmentInspectionRepository inspectionRepository,
            JdbcTemplate jdbcTemplate,
            EntityManager entityManager) {
        this.equipmentRepository = equipmentRepository;
        this.inspectionRepository = inspectionRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    @Override
    public List<Equipment> findAvailable(long storeId, String category, String skillLevel) {
        return equipmentRepository.findAvailable(storeId, category, skillLevel)
                .stream()
                .map(EquipmentRow::toDomain)
                .toList();
    }

    @Override
    public List<Equipment> lockAllInAscendingOrder(List<Long> equipmentIds) {
        List<EquipmentRow> rows = equipmentRepository.findAllByIdInOrderForUpdate(equipmentIds);
        // A preceding availability query may have cached the row before another transaction
        // committed. Acquiring a lock alone does not refresh an already managed entity.
        // Refresh only these locked rows, never clear unrelated caller aggregates.
        rows.forEach(row -> entityManager.refresh(row, LockModeType.PESSIMISTIC_WRITE));
        return rows.stream().map(EquipmentRow::toDomain).toList();
    }

    @Override
    public void saveAll(List<Equipment> equipment) {
        List<Long> equipmentIds = equipment.stream().map(Equipment::equipmentId).toList();
        List<EquipmentRow> rows = equipmentRepository.findAllByIdInOrderForUpdate(equipmentIds);
        if (rows.size() != equipment.size()) {
            throw new IllegalStateException("Locked equipment disappeared during update.");
        }
        for (int index = 0; index < rows.size(); index++) {
            rows.get(index).apply(equipment.get(index));
        }
        equipmentRepository.flush();
    }

    @Override
    public boolean rentalContractReferencesEquipment(long rentalContractId, long equipmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rental_item WHERE contract_id = ? AND equipment_id = ?",
                Integer.class,
                rentalContractId,
                equipmentId) == 1;
    }

    @Override
    public boolean rentalItemReferencesEquipment(long rentalItemId, long equipmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rental_item WHERE rental_item_id = ? AND equipment_id = ?",
                Integer.class,
                rentalItemId,
                equipmentId) == 1;
    }

    @Override
    public boolean repairTaskReferencesEquipment(long repairTaskId, long equipmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM repair_task WHERE repair_task_id = ? AND equipment_id = ?",
                Integer.class,
                repairTaskId,
                equipmentId) == 1;
    }

    @Override
    public boolean transferReferencesEquipment(long transferId, long equipmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM equipment_transfer_item WHERE transfer_id = ? AND equipment_id = ?",
                Integer.class,
                transferId,
                equipmentId) == 1;
    }

    @Override
    public long appendInspection(EquipmentInspection inspection) {
        EquipmentInspectionRow saved = inspectionRepository.saveAndFlush(new EquipmentInspectionRow(inspection));
        return saved.inspectionId();
    }
}
