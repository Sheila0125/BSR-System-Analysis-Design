package com.bsr.repair.web;

import static com.bsr.repair.web.RepairBoundary.call;
import static com.bsr.repair.web.RepairBoundary.date;
import static com.bsr.repair.web.RepairBoundary.decimal;
import static com.bsr.repair.web.RepairBoundary.id;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.repair.application.RepairContractorService;
import com.bsr.repair.application.RepairViews;
import com.bsr.repair.domain.RepairRatePeriod;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repair-contractors")
public class RepairContractorController {

    private final RepairContractorService service;
    private final AccountAuthentication accounts;

    public RepairContractorController(RepairContractorService service,
            AccountAuthentication accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @GetMapping
    List<ContractorView> list(@RequestParam String storeId, Authentication auth) {
        return call(() -> service.list(actor(auth), id(storeId)).stream()
                .map(ContractorView::from).toList());
    }

    @PostMapping
    ContractorView create(@RequestBody ContractorInput body, Authentication auth) {
        return call(() -> ContractorView.from(service.create(actor(auth), id(body.storeId()),
                body.name(), body.contactName(), body.email(), body.phone(), body.postalAddress())));
    }

    @PostMapping("/{contractorId}/update")
    ContractorView update(@PathVariable String contractorId, @RequestBody ContractorInput body,
            Authentication auth) {
        return call(() -> ContractorView.from(service.update(actor(auth), id(body.storeId()),
                id(contractorId), body.name(), body.contactName(), body.email(), body.phone(), body.postalAddress())));
    }

    @PostMapping("/{contractorId}/deactivate")
    ContractorView deactivate(@PathVariable String contractorId, @RequestBody StoreInput body,
            Authentication auth) {
        return call(() -> ContractorView.from(service.deactivate(actor(auth), id(body.storeId()),
                id(contractorId))));
    }

    @GetMapping("/{contractorId}/rates")
    List<RateView> rates(@PathVariable String contractorId, @RequestParam String storeId,
            Authentication auth) {
        return call(() -> service.rates(actor(auth), id(storeId), id(contractorId)).stream()
                .map(RateView::from).toList());
    }

    @PostMapping("/{contractorId}/rates")
    RateView addRate(@PathVariable String contractorId, @RequestBody RateInput body,
            Authentication auth) {
        return call(() -> RateView.from(service.addRate(actor(auth), id(body.storeId()),
                new RepairRatePeriod(id(contractorId), date(body.effectiveFrom()),
                        body.effectiveTo() == null ? null : date(body.effectiveTo()),
                        decimal(body.hourlyRate())))));
    }

    private ActorContext actor(Authentication auth) {
        return accounts.loadActor(((BsrAccountPrincipal) auth.getPrincipal()).accountId());
    }

    record ContractorInput(String storeId, String name, String contactName,
            String email, String phone, String postalAddress) { }
    record StoreInput(String storeId) { }
    record RateInput(String storeId, String effectiveFrom, String effectiveTo,
            String hourlyRate) { }

    record ContractorView(String contractorId, String number, String name,
            String contactName, String email, String phone, boolean active, String postalAddress) {
        static ContractorView from(RepairViews.Contractor value) {
            return new ContractorView(Long.toString(value.id()), value.number(),
                    value.name(), value.contactName(), value.email(), value.phone(), value.active(), value.postalAddress());
        }
    }

    record RateView(String rateId, String contractorId, String effectiveFrom,
            String effectiveTo, String hourlyRate) {
        static RateView from(RepairViews.Rate value) {
            return new RateView(Long.toString(value.id()), Long.toString(value.contractorId()),
                    value.effectiveFrom().toString(), value.effectiveTo() == null
                            ? null : value.effectiveTo().toString(), value.hourlyRate().toPlainString());
        }
    }
}
