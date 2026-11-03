package com.bsr.equipment.infrastructure.persistence;

import com.bsr.equipment.application.EquipmentCatalogPersistence;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEquipmentCatalog implements EquipmentCatalogPersistence {
    private final JdbcTemplate jdbc;
    public JdbcEquipmentCatalog(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final RowMapper<Entry> ROW = (r, index) -> new Entry(r.getLong("equipment_id"),
            r.getString("inventory_number"), r.getString("equipment_category"), r.getLong("home_store_id"),
            r.getLong("current_store_id"), r.getString("equipment_status"), r.getLong("catalog_version"),
            new Metadata(r.getString("equipment_name"), r.getString("skill_level"), r.getString("description"),
                    r.getObject("purchase_date", java.time.LocalDate.class), r.getBigDecimal("purchase_price"),
                    r.getBigDecimal("retail_price"), r.getBigDecimal("current_daily_rate")));

    @Override public List<Entry> find(long storeId, String status, String inventory, String category, String skill) {
        StringBuilder sql = new StringBuilder("SELECT * FROM equipment WHERE current_store_id=?");
        List<Object> args = new ArrayList<>(); args.add(storeId);
        String[] columns = {"equipment_status", "inventory_number", "equipment_category", "skill_level"};
        String[] filters = {status, inventory, category, skill};
        for (int i=0; i<columns.length; i++) {
            if (filters[i] != null) { sql.append(" AND ").append(columns[i]).append("=?"); args.add(filters[i]); }
        }
        sql.append(" ORDER BY inventory_number, equipment_id");
        return jdbc.query(sql.toString(), ROW, args.toArray());
    }

    @Override public Optional<Entry> lock(long id) {
        return jdbc.query("SELECT * FROM equipment WHERE equipment_id=? FOR UPDATE", ROW, id).stream().findFirst();
    }

    @Override public Optional<Entry> findById(long id) {
        return jdbc.query("SELECT * FROM equipment WHERE equipment_id=?", ROW, id).stream().findFirst();
    }

    @Override public Entry insert(long storeId, String inventory, String category, Metadata m) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO equipment(inventory_number,equipment_name,equipment_category,skill_level,
                    home_store_id,current_store_id,equipment_status,description,purchase_date,purchase_price,
                    retail_price,current_daily_rate) VALUES(?,?,?,?,?,?,'AVAILABLE',?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1,inventory); statement.setString(2,m.name()); statement.setString(3,category);
            statement.setString(4,m.skillLevel()); statement.setLong(5,storeId); statement.setLong(6,storeId);
            statement.setString(7,m.description()); statement.setDate(8,m.purchaseDate()==null?null:Date.valueOf(m.purchaseDate()));
            statement.setBigDecimal(9,m.purchasePrice()); statement.setBigDecimal(10,m.retailPrice());
            statement.setBigDecimal(11,m.currentDailyRate()); return statement;
        }, key);
        return new Entry(key.getKey().longValue(),inventory,category,storeId,storeId,"AVAILABLE",0,m);
    }

    @Override public Entry update(Entry e, Metadata m) {
        // Explicit metadata allow-list: lifecycle state and store locations are not editable here.
        jdbc.update("""
                UPDATE equipment SET equipment_name=?,skill_level=?,description=?,purchase_date=?,
                purchase_price=?,retail_price=?,current_daily_rate=?,catalog_version=catalog_version+1
                WHERE equipment_id=?
                """,m.name(),m.skillLevel(),m.description(),m.purchaseDate(),m.purchasePrice(),m.retailPrice(),
                m.currentDailyRate(),e.id());
        return new Entry(e.id(),e.inventoryNumber(),e.category(),e.homeStoreId(),e.currentStoreId(),
                e.status(),e.revision()+1,m);
    }
}
