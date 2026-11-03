package com.bsr.audit.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bsr.audit.api.AuditAppender;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.IdentityType;
import com.bsr.schema.IsolatedTestDatabaseInitializer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ContextConfiguration(initializers = IsolatedTestDatabaseInitializer.class)
@Transactional
class AuditUtcPersistenceIT {
    @Autowired private AuditAppender audit;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    @Test
    @DisplayName("AC-BR40 audit occurred_at stores the actual UTC instant without host-zone displacement")
    void utcInstantRoundTripsThroughMySqlDatetime() {
        jdbc.update("INSERT INTO employee(employee_id,employee_number,full_name,email,business_status) "
                + "VALUES(43003,'E-T03-UTC','UTC Audit Actor','t03-audit-utc@test.invalid','ACTIVE')");
        jdbc.update("INSERT INTO account(account_id,login_name,password_hash,enabled,employee_id) "
                + "VALUES(43004,'t03-audit-utc','{noop}unused',TRUE,43003)");
        ActorContext actor = new ActorContext(43004L, "t03-audit-utc",
                IdentityType.EMPLOYEE, 43003L, List.of());
        LocalDateTime before = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusSeconds(2);
        audit.appendSuccess(actor, "EMPLOYEE_SHIFT", 43003L, "SHIFT_CREATED", "UTC mapping regression");
        LocalDateTime after = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusSeconds(2);

        String raw = jdbc.queryForObject("SELECT DATE_FORMAT(occurred_at,'%Y-%m-%d %H:%i:%s') "
                + "FROM audit_event WHERE actor_account_id=? AND object_type=? AND object_id=?",
                String.class, 43004L, "EMPLOYEE_SHIFT", 43003L);
        LocalDateTime stored = LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertTrue(!stored.isBefore(before) && !stored.isAfter(after),
                "Audit timestamp must be persisted as UTC, not shifted by the host timezone.");
    }
}
