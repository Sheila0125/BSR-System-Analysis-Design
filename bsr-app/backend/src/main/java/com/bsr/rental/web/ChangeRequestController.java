package com.bsr.rental.web;

import static com.bsr.rental.web.RentalBoundary.*;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.rental.application.ChangePersistence.StoredChange;
import com.bsr.rental.application.ChangeRequestService;
import com.bsr.rental.domain.RentalChangeRequest;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChangeRequestController {
    private final ChangeRequestService service;
    private final AccountAuthentication accounts;
    public ChangeRequestController(ChangeRequestService service,AccountAuthentication accounts) { this.service=service; this.accounts=accounts; }
    @PostMapping("/api/contracts/{contractId}/change-requests")
    View submit(@PathVariable String contractId,@RequestBody Request body,Authentication auth) {
        return call(()->View.from(service.submit(actor(auth),id(contractId),id(body.requestedStoreId()),enumValue(RentalChangeRequest.Type.class,body.type()),body.note(),
                body.requestedReturnItemIds()==null?List.of():body.requestedReturnItemIds().stream().map(value -> id(value)).toList())));
    }
    @GetMapping("/api/contracts/{contractId}/changes")
    List<View> list(@PathVariable String contractId,Authentication auth) { return call(()->service.forContract(actor(auth),id(contractId)).stream().map(View::from).toList()); }
    @GetMapping("/api/rental-change-requests")
    List<View> queue(@RequestParam String storeId,Authentication auth) { return call(()->service.queue(actor(auth),id(storeId)).stream().map(View::from).toList()); }
    private ActorContext actor(Authentication auth) { return accounts.loadActor(((BsrAccountPrincipal)auth.getPrincipal()).accountId()); }
    record Request(String requestedStoreId,String type,String note,List<String> requestedReturnItemIds) { }
    record View(String changeId,String contractId,String changeNumber,String type,String status,String requestedStoreId,
            String requestedAt,String note,String handledByEmployeeId,String effectiveDate,String amountDelta,String changedAt,List<String> requestedReturnItemIds) {
        static View from(StoredChange c) { return new View(Long.toString(c.id()),Long.toString(c.contractId()),c.number(),c.type(),c.status(),
                c.requestedStoreId()==null?null:c.requestedStoreId().toString(),c.requestedAt()==null?null:c.requestedAt()+"Z",c.note(),
                c.handledByEmployeeId()==null?null:c.handledByEmployeeId().toString(),c.effectiveDate()==null?null:c.effectiveDate().toString(),
                c.amountDelta()==null?null:c.amountDelta().toPlainString(),c.changedAt()==null?null:c.changedAt()+"Z",c.requestedReturnItemIds().stream().map(String::valueOf).toList()); }
    }
}
