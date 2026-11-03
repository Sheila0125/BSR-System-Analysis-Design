package com.bsr.audit.application;

import com.bsr.audit.api.AuditObjectScopeResolver;
import com.bsr.audit.infrastructure.persistence.AuditEventRepository;
import com.bsr.audit.infrastructure.persistence.AuditEventRow;
import com.bsr.identity.api.ActionPermission;
import com.bsr.identity.api.ActorContext;
import com.bsr.identity.api.Authorization;
import com.bsr.platform.error.ApiException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {

    private final Authorization authorization;
    private final List<AuditObjectScopeResolver> scopeResolvers;
    private final AuditEventRepository auditEventRepository;

    public AuditQueryService(
            Authorization authorization,
            List<AuditObjectScopeResolver> scopeResolvers,
            AuditEventRepository auditEventRepository) {
        this.authorization = authorization;
        this.scopeResolvers = List.copyOf(scopeResolvers);
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> findByObject(ActorContext actor, String objectType, long objectId) {
        authorization.require(actor, ActionPermission.MANAGER_AUDIT_READ, null);
        AuditObjectScopeResolver resolver = scopeResolvers.stream()
                .filter(candidate -> candidate.supports(objectType))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "AUDIT_SCOPE_UNAVAILABLE",
                        "Audit access cannot be authorized for this object type."));
        resolver.requireRead(actor, objectId);
        return auditEventRepository
                .findTop200ByObjectTypeAndObjectIdOrderByOccurredAtDescAuditEventIdDesc(objectType, objectId)
                .stream()
                .map(AuditEventView::from)
                .toList();
    }

    public record AuditEventView(
            String auditEventId,
            String actorAccountId,
            String objectType,
            String objectId,
            String actionCode,
            OffsetDateTime occurredAt,
            String resultCode,
            String detailSummary) {

        static AuditEventView from(AuditEventRow row) {
            return new AuditEventView(
                    Long.toString(row.auditEventId()),
                    Long.toString(row.actorAccountId()),
                    row.objectType(),
                    Long.toString(row.objectId()),
                    row.actionCode(),
                    row.occurredAt().atOffset(ZoneOffset.UTC),
                    row.resultCode(),
                    row.detailSummary());
        }
    }
}
