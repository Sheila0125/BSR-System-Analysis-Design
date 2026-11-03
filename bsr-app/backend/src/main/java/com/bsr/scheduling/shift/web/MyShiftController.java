package com.bsr.scheduling.shift.web;

import static com.bsr.scheduling.shift.web.ShiftBoundary.call;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.scheduling.shift.application.ShiftService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/shifts")
public class MyShiftController {
    private final ShiftService service;
    private final AccountAuthentication accounts;

    public MyShiftController(ShiftService service, AccountAuthentication accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @GetMapping
    List<ShiftController.ShiftView> myShifts(Authentication auth) {
        return call(() -> service.myShifts(accounts.loadActor(
                ((BsrAccountPrincipal) auth.getPrincipal()).accountId())).stream()
                .map(ShiftController.ShiftView::from).toList());
    }
}
