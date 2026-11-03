package com.bsr.rental.web;

import static com.bsr.rental.web.RentalBoundary.*;
import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.equipment.api.InspectionOutcome;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.rental.application.AdjustmentService;
import com.bsr.rental.application.RentalService;
import com.bsr.rental.domain.RentalChangeRequest;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdjustmentController {
    private final AdjustmentService service;
    private final AccountAuthentication accounts;
    public AdjustmentController(AdjustmentService service,AccountAuthentication accounts) { this.service=service; this.accounts=accounts; }
    @PostMapping("/api/contracts/{contractId}/changes")
    View start(@PathVariable String contractId,@RequestBody Start body,Authentication auth) {
        return call(() -> {
            var actor=actor(auth); var c=service.start(actor,id(contractId),id(body.storeId()),enumValue(RentalChangeRequest.Type.class,body.type()));
            return View.from(service.read(actor,c.id()));
        });
    }
    @GetMapping("/api/rental-changes/{changeId}")
    View read(@PathVariable String changeId,Authentication auth) { return call(() -> View.from(service.read(actor(auth),id(changeId)))); }
    @PostMapping("/api/rental-changes/{changeId}/receive")
    View receive(@PathVariable String changeId,@RequestBody Receive body,Authentication auth) {
        return call(() -> {
            var actor=actor(auth); long id=id(changeId);
            service.receive(actor,id,body.items()==null?null:body.items().stream().map(i ->
                    new RentalService.ReturnItem(id(required(i).itemId()),enumValue(InspectionOutcome.class,i.outcome()),i.faultDescription())).toList());
            return View.from(service.read(actor,id));
        });
    }
    @PostMapping("/api/rental-changes/{changeId}/quote")
    View quote(@PathVariable String changeId,@RequestBody Quote body,Authentication auth) {
        return call(() -> {
            var actor=actor(auth); long id=id(changeId);
            service.quote(actor,id,body.selections()==null?null:body.selections().stream().map(s ->
                    new RentalService.Selection(id(required(s).equipmentId()),money(s.expectedDailyRate()))).toList());
            return View.from(service.read(actor,id));
        });
    }
    @PostMapping("/api/rental-changes/{changeId}/confirm")
    View confirm(@PathVariable String changeId,Authentication auth) { return decide(changeId,auth,true); }
    @PostMapping("/api/rental-changes/{changeId}/reject")
    View reject(@PathVariable String changeId,Authentication auth) { return decide(changeId,auth,false); }
    private View decide(String changeId,Authentication auth,boolean confirm) {
        return call(() -> { var actor=actor(auth); long id=id(changeId); service.decide(actor,id,confirm); return View.from(service.read(actor,id)); });
    }
    @PostMapping("/api/rental-changes/{changeId}/stop")
    View stop(@PathVariable String changeId,Authentication auth) {
        return call(() -> { var actor=actor(auth); long id=id(changeId); service.stop(actor,id); return View.from(service.read(actor,id)); });
    }
    private ActorContext actor(Authentication auth) { return accounts.loadActor(((BsrAccountPrincipal)auth.getPrincipal()).accountId()); }
    record Start(String storeId,String type) { }
    record Receive(List<Returned> items) { }
    record Returned(String itemId,String outcome,String faultDescription) { }
    record Quote(List<Selection> selections) { }
    record Selection(String equipmentId,String expectedDailyRate) { }
    record Item(String itemId,String equipmentId,String status,String returnedAt,String returnStoreId,boolean active,String effect,String dailyRateSnapshot) { }
    record View(ChangeRequestController.View change,List<Item> items,List<RentalController.ChargeView> charges) {
        static View from(AdjustmentService.Detail detail) {
            return new View(ChangeRequestController.View.from(detail.change()),detail.items().stream().map(i -> {
                var effect=detail.effects().stream().filter(e -> e.itemId()==i.id()).findFirst();
                return new Item(Long.toString(i.id()),Long.toString(i.equipmentId()),i.status(),i.returnedAt()==null?null:i.returnedAt()+"Z",
                        i.returnStoreId()==null?null:i.returnStoreId().toString(),i.active(),effect.map(e -> e.effect()).orElse(null),
                        effect.isPresent()?i.dailyRateSnapshot().toPlainString():null);
            }).toList(),detail.charges().stream().map(RentalController.ChargeView::from).toList());
        }
    }
}
