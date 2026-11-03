package com.bsr.audit.api;

import com.bsr.identity.api.ActorContext;

public interface AuditAppender {

    void appendSuccess(
            ActorContext actor,
            String objectType,
            long objectId,
            String actionCode,
            String detailSummary);
}
