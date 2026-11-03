package com.bsr.audit.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEventRow, Long> {

    List<AuditEventRow> findTop200ByObjectTypeAndObjectIdOrderByOccurredAtDescAuditEventIdDesc(
            String objectType,
            Long objectId);
}
