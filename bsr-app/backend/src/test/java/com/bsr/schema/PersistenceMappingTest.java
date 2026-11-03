package com.bsr.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

class PersistenceMappingTest {

    private static final Set<Class<?>> SCALAR_TYPES = Set.of(
            Long.class,
            Integer.class,
            Short.class,
            Boolean.class,
            String.class,
            BigDecimal.class,
            LocalDate.class,
            LocalDateTime.class,
            LocalTime.class);

    private static final List<Mapping> MAPPINGS = List.of(
            new Mapping(com.bsr.customerrequest.infrastructure.persistence.CustomerRow.class, com.bsr.customerrequest.infrastructure.persistence.CustomerRepository.class, "customer", 13),
            new Mapping(com.bsr.scheduling.infrastructure.persistence.EmployeeRow.class, com.bsr.scheduling.infrastructure.persistence.EmployeeRepository.class, "employee", 8),
            new Mapping(com.bsr.repair.infrastructure.persistence.RepairContractorRow.class, com.bsr.repair.infrastructure.persistence.RepairContractorRepository.class, "repair_contractor", 8),
            new Mapping(com.bsr.storetransfer.infrastructure.persistence.StoreRow.class, com.bsr.storetransfer.infrastructure.persistence.StoreRepository.class, "store", 7),
            new Mapping(com.bsr.identity.infrastructure.persistence.AccountRow.class, com.bsr.identity.infrastructure.persistence.AccountRepository.class, "account", 7),
            new Mapping(com.bsr.identity.infrastructure.persistence.RoleGrantRow.class, com.bsr.identity.infrastructure.persistence.RoleGrantRepository.class, "role_grant", 6),
            new Mapping(com.bsr.equipment.infrastructure.persistence.EquipmentRow.class, com.bsr.equipment.infrastructure.persistence.EquipmentRepository.class, "equipment", 11),
            new Mapping(com.bsr.customerrequest.infrastructure.persistence.RentalRequestRow.class, com.bsr.customerrequest.infrastructure.persistence.RentalRequestRepository.class, "rental_request", 10),
            new Mapping(com.bsr.customerrequest.infrastructure.persistence.RentalRequestItemRow.class, com.bsr.customerrequest.infrastructure.persistence.RentalRequestItemRepository.class, "rental_request_item", 5),
            new Mapping(com.bsr.customerrequest.infrastructure.persistence.RentalRequestSupplementRow.class, com.bsr.customerrequest.infrastructure.persistence.RentalRequestSupplementRepository.class, "rental_request_supplement", 8),
            new Mapping(com.bsr.rental.infrastructure.persistence.RentalContractRow.class, com.bsr.rental.infrastructure.persistence.RentalContractRepository.class, "rental_contract", 11),
            new Mapping(com.bsr.rental.infrastructure.persistence.RentalItemRow.class, com.bsr.rental.infrastructure.persistence.RentalItemRepository.class, "rental_item", 8),
            new Mapping(com.bsr.rental.infrastructure.persistence.RentalChangeRow.class, com.bsr.rental.infrastructure.persistence.RentalChangeRepository.class, "rental_change", 14),
            new Mapping(com.bsr.rental.infrastructure.persistence.RentalChangeItemRow.class, com.bsr.rental.infrastructure.persistence.RentalChangeItemRepository.class, "rental_change_item", 4),
            new Mapping(com.bsr.rental.infrastructure.persistence.RentalChargeRow.class, com.bsr.rental.infrastructure.persistence.RentalChargeRepository.class, "rental_charge", 7),
            new Mapping(com.bsr.rental.infrastructure.persistence.PaymentTransactionRow.class, com.bsr.rental.infrastructure.persistence.PaymentTransactionRepository.class, "payment_transaction", 11),
            new Mapping(com.bsr.repair.infrastructure.persistence.RepairTaskRow.class, com.bsr.repair.infrastructure.persistence.RepairTaskRepository.class, "repair_task", 13),
            new Mapping(com.bsr.equipment.infrastructure.persistence.EquipmentInspectionRow.class, com.bsr.equipment.infrastructure.persistence.EquipmentInspectionRepository.class, "equipment_inspection", 9),
            new Mapping(com.bsr.repair.infrastructure.persistence.RepairRateRow.class, com.bsr.repair.infrastructure.persistence.RepairRateRepository.class, "repair_rate", 6),
            new Mapping(com.bsr.repair.infrastructure.persistence.RepairLaborRow.class, com.bsr.repair.infrastructure.persistence.RepairLaborRepository.class, "repair_labor", 11),
            new Mapping(com.bsr.repair.infrastructure.persistence.RepairServiceChargeRow.class, com.bsr.repair.infrastructure.persistence.RepairServiceChargeRepository.class, "repair_service_charge", 7),
            new Mapping(com.bsr.storetransfer.infrastructure.persistence.EquipmentTransferRow.class, com.bsr.storetransfer.infrastructure.persistence.EquipmentTransferRepository.class, "equipment_transfer", 11),
            new Mapping(com.bsr.storetransfer.infrastructure.persistence.EquipmentTransferItemRow.class, com.bsr.storetransfer.infrastructure.persistence.EquipmentTransferItemRepository.class, "equipment_transfer_item", 10),
            new Mapping(com.bsr.scheduling.infrastructure.persistence.EmployeeShiftRow.class, com.bsr.scheduling.infrastructure.persistence.EmployeeShiftRepository.class, "employee_shift", 11),
            new Mapping(com.bsr.audit.infrastructure.persistence.AuditEventRow.class, com.bsr.audit.infrastructure.persistence.AuditEventRepository.class, "audit_event", 8));

    @Test
    @DisplayName("AC-BR01 schema rows map 24 foundation tables plus approved supplement history")
    void AC_BR01_rowsMapAllBusinessTablesAndColumns() throws Exception {
        assertEquals(25, MAPPINGS.size());
        assertEquals(224, MAPPINGS.stream().mapToInt(Mapping::columns).sum());
        assertEquals(25, MAPPINGS.stream().map(Mapping::table).distinct().count());

        for (Mapping mapping : MAPPINGS) {
            Entity entity = mapping.row().getAnnotation(Entity.class);
            Table table = mapping.row().getAnnotation(Table.class);
            assertNotNull(entity, mapping.row().getName());
            assertNotNull(table, mapping.row().getName());
            assertEquals(mapping.row().getSimpleName(), entity.name());
            assertEquals(mapping.table(), table.name());

            List<Field> fields = Arrays.stream(mapping.row().getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .toList();
            assertEquals(mapping.columns(), fields.size(), mapping.table());

            for (Field field : fields) {
                Column column = field.getAnnotation(Column.class);
                assertNotNull(column, mapping.table() + "." + field.getName());
                assertEquals(toSnakeCase(field.getName()), column.name());
                assertTrue(SCALAR_TYPES.contains(field.getType()), field.toString());
                assertFalse(field.getType().getSimpleName().endsWith("Row"), field.toString());
            }

            List<Field> identifiers = fields.stream()
                    .filter(field -> field.isAnnotationPresent(Id.class))
                    .toList();
            assertEquals(1, identifiers.size(), mapping.table());
            GeneratedValue generatedValue = identifiers.getFirst().getAnnotation(GeneratedValue.class);
            assertNotNull(generatedValue, mapping.table());
            assertEquals(GenerationType.IDENTITY, generatedValue.strategy());

            assertTrue(
                    Modifier.isProtected(mapping.row().getDeclaredConstructor().getModifiers()),
                    mapping.row().getName());
            assertTrue(JpaRepository.class.isAssignableFrom(mapping.repository()), mapping.repository().getName());
            Class<?> repositoryRow = ResolvableType.forClass(mapping.repository())
                    .as(JpaRepository.class)
                    .getGeneric(0)
                    .resolve();
            assertEquals(mapping.row(), repositoryRow, mapping.repository().getName());
        }
    }

    @Test
    @DisplayName("AC-BR33 parent-row repositories expose pessimistic write locks")
    void AC_BR33_parentRowRepositoriesExposePessimisticWriteLocks() throws Exception {
        assertPessimisticWrite(
                com.bsr.scheduling.infrastructure.persistence.EmployeeRepository.class,
                "findByIdForUpdate",
                Long.class);
        assertPessimisticWrite(
                com.bsr.repair.infrastructure.persistence.RepairContractorRepository.class,
                "findByIdForUpdate",
                Long.class);
        assertPessimisticWrite(
                com.bsr.storetransfer.infrastructure.persistence.StoreRepository.class,
                "findByIdForUpdate",
                Long.class);

        Method equipmentLock = assertPessimisticWrite(
                com.bsr.equipment.infrastructure.persistence.EquipmentRepository.class,
                "findAllByIdInOrderForUpdate",
                Collection.class);
        Query query = equipmentLock.getAnnotation(Query.class);
        assertNotNull(query);
        assertTrue(query.value().contains("order by row.equipmentId"));
    }

    private static Method assertPessimisticWrite(
            Class<?> repository,
            String methodName,
            Class<?> parameterType) throws Exception {
        Method method = repository.getDeclaredMethod(methodName, parameterType);
        Lock lock = method.getAnnotation(Lock.class);
        assertNotNull(lock, repository.getName() + "." + methodName);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
        return method;
    }

    private static String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private record Mapping(Class<?> row, Class<?> repository, String table, int columns) {
    }
}
