package com.bsr.rental.web;

import static com.bsr.rental.web.RentalBoundary.*;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.equipment.api.InspectionOutcome;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.rental.application.RentalPersistence;
import com.bsr.rental.application.RentalPersistence.StoredContract;
import com.bsr.rental.application.RentalService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts")
public class RentalController {
    private final RentalService service;
    private final AccountAuthentication accounts;
    public RentalController(RentalService service, AccountAuthentication accounts) { this.service=service; this.accounts=accounts; }
    @GetMapping("/customer")
    List<ContractView> customer(Authentication auth) { return call(()->service.listCustomer(actor(auth)).stream().map(ContractView::from).toList()); }
    @GetMapping
    List<ContractView> employee(@RequestParam String storeId,Authentication auth) {
        return call(()->service.listEmployee(actor(auth),id(storeId)).stream().map(ContractView::from).toList());
    }
    @GetMapping("/{contractId}")
    ContractView read(@PathVariable String contractId,Authentication auth) { return call(()->ContractView.from(service.read(actor(auth),id(contractId)))); }
    @PostMapping
    ContractView create(@RequestBody Creation body,Authentication auth) {
        return call(()->ContractView.from(service.create(actor(auth),id(body.storeId()),id(body.sourceRequestId()),
                body.selections()==null ? null : body.selections().stream().map(s -> new RentalService.Selection(id(required(s).equipmentId()),money(s.expectedDailyRate()))).toList())));
    }
    @PostMapping("/{contractId}/submit")
    ContractView submit(@PathVariable String contractId,Authentication auth) { return call(()->ContractView.from(service.submit(actor(auth),id(contractId)))); }
    @PostMapping("/{contractId}/revise")
    ContractView revise(@PathVariable String contractId,@RequestBody Revision body,Authentication auth) {
        return call(() -> ContractView.from(service.revise(actor(auth),id(contractId),
                body.replacedItemIds()==null?null:body.replacedItemIds().stream().map(RentalBoundary::id).toList(),
                body.selections()==null?null:body.selections().stream().map(s -> new RentalService.Selection(id(required(s).equipmentId()),money(s.expectedDailyRate()))).toList())));
    }
    @PostMapping("/{contractId}/confirm")
    ContractView confirm(@PathVariable String contractId,Authentication auth) { return call(()->ContractView.from(service.decide(actor(auth),id(contractId),true))); }
    @PostMapping("/{contractId}/reject")
    ContractView reject(@PathVariable String contractId,Authentication auth) { return call(()->ContractView.from(service.decide(actor(auth),id(contractId),false))); }
    @PostMapping("/{contractId}/cancel")
    ContractView cancel(@PathVariable String contractId,Authentication auth) { return call(()->ContractView.from(service.cancel(actor(auth),id(contractId)))); }
    @PostMapping("/{contractId}/issue")
    ContractView issue(@PathVariable String contractId,@RequestBody Issue body,Authentication auth) {
        return call(()->ContractView.from(service.issue(actor(auth),id(contractId),body.itemIds()==null ? null : body.itemIds().stream().map(RentalBoundary::id).toList())));
    }
    @GetMapping("/{contractId}/return-lookup")
    ReturnView lookup(@PathVariable String contractId,@RequestParam String returnStoreId,Authentication auth) {
        return call(()->ReturnView.from(service.returnLookup(actor(auth),id(contractId),id(returnStoreId))));
    }
    @PostMapping("/{contractId}/issue-at-store")
    ReturnView issueAtStore(@PathVariable String contractId,@RequestBody StoreIssue body,Authentication auth) {
        return call(() -> ReturnView.from(service.issueAtStore(actor(auth),id(contractId),id(body.storeId()),
                body.itemIds()==null?null:body.itemIds().stream().map(RentalBoundary::id).toList())));
    }
    @PostMapping("/{contractId}/returns")
    ReturnView returns(@PathVariable String contractId,@RequestBody Return body,Authentication auth) {
        return call(()->ReturnView.from(service.returnItems(actor(auth),id(contractId),id(body.returnStoreId()),
                body.items()==null ? null : body.items().stream().map(i -> new RentalService.ReturnItem(id(required(i).itemId()),
                        enumValue(InspectionOutcome.class,i.outcome()),i.faultDescription())).toList())));
    }
    private ActorContext actor(Authentication auth) { return accounts.loadActor(((BsrAccountPrincipal)auth.getPrincipal()).accountId()); }
    record Creation(String storeId,String sourceRequestId,List<Selection> selections) { }
    record Selection(String equipmentId,String expectedDailyRate) { }
    record Revision(List<String> replacedItemIds,List<Selection> selections) { }
    record Issue(List<String> itemIds) { }
    record StoreIssue(String storeId,List<String> itemIds) { }
    record Return(String returnStoreId,List<Returned> items) { }
    record Returned(String itemId,String outcome,String faultDescription) { }
    record ItemView(String itemId,String equipmentId,String dailyRateSnapshot,String status,
            String checkedOutAt,String returnedAt,String returnStoreId,boolean active) {
        static ItemView from(RentalPersistence.StoredItem i) {
            return new ItemView(Long.toString(i.id()),Long.toString(i.equipmentId()),i.dailyRateSnapshot().toPlainString(),i.status(),
                    i.checkedOutAt()==null?null:i.checkedOutAt()+"Z",i.returnedAt()==null?null:i.returnedAt()+"Z",i.returnStoreId()==null?null:i.returnStoreId().toString(),i.active());
        }
    }
    record ChargeView(String chargeId,String type,String amount,String status,String createdAt) {
        static ChargeView from(RentalPersistence.Charge c) { return new ChargeView(Long.toString(c.id()),c.type(),c.amount().toPlainString(),c.status(),c.createdAt()+"Z"); }
    }
    record ContractView(String contractId,String contractNumber,String customerId,String serviceStoreId,String sourceRequestId,
            String contractDate,String rentalStartDate,int rentalDays,String amountDue,String status,List<ItemView> items,List<ChargeView> charges) {
        static ContractView from(StoredContract s) {
            var c=s.contract();
            return new ContractView(Long.toString(s.id()),c.contractNumber(),Long.toString(c.customerId()),Long.toString(c.serviceStoreId()),Long.toString(c.sourceRequestId()),
                    c.contractDate().toString(),c.rentalStartDate().toString(),c.rentalDays(),c.amountDue().toPlainString(),c.status().name(),
                    s.items().stream().map(ItemView::from).toList(),s.charges().stream().map(ChargeView::from).toList());
        }
    }
    record ReturnView(String contractId,String status,List<ReturnItemView> items) {
        static ReturnView from(RentalService.ReturnReceipt r) { return new ReturnView(Long.toString(r.contractId()),r.status(),r.items().stream().map(ReturnItemView::from).toList()); }
    }
    record ReturnItemView(String itemId,String equipmentId,String status,String returnedAt,String returnStoreId) {
        static ReturnItemView from(RentalPersistence.StoredItem i) { return new ReturnItemView(Long.toString(i.id()),Long.toString(i.equipmentId()),i.status(),i.returnedAt()==null?null:i.returnedAt()+"Z",i.returnStoreId()==null?null:i.returnStoreId().toString()); }
    }
}
