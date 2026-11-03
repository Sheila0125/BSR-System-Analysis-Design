package com.bsr.rental.web;

import static com.bsr.rental.web.RentalBoundary.*;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.rental.application.TransactionPersistence.RecordedTransaction;
import com.bsr.rental.application.TransactionService;
import com.bsr.rental.domain.ExternalTransactionResult;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rental-charges/{chargeId}/transactions")
public class TransactionController {
    private final TransactionService service;
    private final AccountAuthentication accounts;
    public TransactionController(TransactionService service,AccountAuthentication accounts) { this.service=service; this.accounts=accounts; }
    @GetMapping
    List<View> list(@PathVariable String chargeId,Authentication auth) {
        return call(()->service.list(actor(auth),id(chargeId)).stream().map(View::from).toList());
    }
    @PostMapping
    View record(@PathVariable String chargeId,@RequestBody Result body,Authentication auth) {
        return call(()->View.from(service.record(actor(auth),new ExternalTransactionResult(id(chargeId),
                enumValue(ExternalTransactionResult.Type.class,body.type()),body.externalId(),money(body.amount()),
                enumValue(ExternalTransactionResult.Status.class,body.status()),body.processedAt()==null ? null
                        : LocalDateTime.ofInstant(Instant.parse(body.processedAt()),ZoneOffset.UTC)))));
    }
    private ActorContext actor(Authentication auth) { return accounts.loadActor(((BsrAccountPrincipal)auth.getPrincipal()).accountId()); }
    record Result(String type,String externalId,String amount,String status,String processedAt) { }
    record View(String transactionId,String chargeId,String type,String externalId,String amount,String status,
            String processedAt,String recordedByEmployeeId,String recordedAt,String source,String simulatedByAccountId) {
        static View from(RecordedTransaction t) { var r=t.result(); return new View(Long.toString(t.id()),Long.toString(r.chargeId()),
                r.type().name(),r.externalId(),r.amount().toPlainString(),r.status().name(),r.processedAt()==null?null:r.processedAt()+"Z",
                t.employeeId()==null ? null : t.employeeId().toString(),t.recordedAt()+"Z",t.source(),
                t.simulatedByAccountId()==null ? null : t.simulatedByAccountId().toString()); }
    }
}
