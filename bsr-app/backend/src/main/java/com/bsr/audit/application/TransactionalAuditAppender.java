package com.bsr.audit.application;

import com.bsr.audit.api.AuditAppender;
import com.bsr.audit.infrastructure.persistence.AuditEventRepository;
import com.bsr.audit.infrastructure.persistence.AuditEventRow;
import com.bsr.identity.api.ActorContext;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalAuditAppender implements AuditAppender {

    private final AuditEventRepository repository;
    private final Clock clock;

    public TransactionalAuditAppender(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendSuccess(
            ActorContext actor,
            String objectType,
            long objectId,
            String actionCode,
            String detailSummary) {
        repository.save(new AuditEventRow(
                actor.accountId(),
                requireCode(objectType, "objectType"),
                objectId,
                requireCode(actionCode, "actionCode"),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                "SUCCESS",
                sanitize(detailSummary)));
    }

    private String requireCode(String value, String name) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,39}")) {
            throw new IllegalArgumentException(name + " must be an uppercase technical code of at most 40 characters.");
        }
        return value;
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String singleLine = value.replaceAll("[\\r\\n\\t]+", " ").strip();
        if (singleLine.length() > 1000) {
            throw new IllegalArgumentException("detailSummary must not exceed 1000 characters.");
        }
        String lowered = singleLine.toLowerCase();
        if (lowered.contains("password")
                || lowered.contains("cookie")
                || lowered.contains("session")
                || lowered.contains("csrf")
                || lowered.contains("token")
                || lowered.contains("authorization")
                || lowered.contains("secret")) {
            throw new IllegalArgumentException("detailSummary must not contain credential or session material.");
        }
        return singleLine;
    }
}
