package com.bsr.rental.web;

import static com.bsr.rental.web.RentalBoundary.*;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.rental.application.TransactionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rental-charges/{chargeId}/simulated-payments")
public class SimulatedPaymentController {
    private final TransactionService service;
    private final AccountAuthentication accounts;
    public SimulatedPaymentController(TransactionService service,AccountAuthentication accounts) {
        this.service=service; this.accounts=accounts;
    }
    @PostMapping
    TransactionController.View simulate(@PathVariable String chargeId,@RequestBody Simulation body,Authentication auth) {
        return call(()->TransactionController.View.from(service.simulate(
                accounts.loadActor(((BsrAccountPrincipal)auth.getPrincipal()).accountId()),id(chargeId),body.idempotencyKey(),body.outcome())));
    }
    record Simulation(String idempotencyKey,String outcome) { }
}
