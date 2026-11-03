package com.bsr.audit.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "AuditEventRow")
@Table(name = "audit_event")
public class AuditEventRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_event_id", nullable = false)
    Long auditEventId;

    @Column(name = "actor_account_id", nullable = false)
    Long actorAccountId;

    @Column(name = "object_type", nullable = false, length = 40)
    String objectType;

    @Column(name = "object_id", nullable = false)
    Long objectId;

    @Column(name = "action_code", nullable = false, length = 40)
    String actionCode;

    @Column(name = "occurred_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    LocalDateTime occurredAt;

    @Column(name = "result_code", nullable = false, length = 20)
    String resultCode;

    @Column(name = "detail_summary", nullable = true, length = 1000)
    String detailSummary;

    protected AuditEventRow() {
    }

    public AuditEventRow(
            long actorAccountId,
            String objectType,
            long objectId,
            String actionCode,
            LocalDateTime occurredAt,
            String resultCode,
            String detailSummary) {
        this.actorAccountId = actorAccountId;
        this.objectType = objectType;
        this.objectId = objectId;
        this.actionCode = actionCode;
        this.occurredAt = occurredAt;
        this.resultCode = resultCode;
        this.detailSummary = detailSummary;
    }

    public Long auditEventId() {
        return auditEventId;
    }

    public Long actorAccountId() {
        return actorAccountId;
    }

    public String objectType() {
        return objectType;
    }

    public Long objectId() {
        return objectId;
    }

    public String actionCode() {
        return actionCode;
    }

    public LocalDateTime occurredAt() {
        return occurredAt;
    }

    public String resultCode() {
        return resultCode;
    }

    public String detailSummary() {
        return detailSummary;
    }
}
