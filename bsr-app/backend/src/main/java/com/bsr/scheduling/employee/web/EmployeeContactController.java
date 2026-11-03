package com.bsr.scheduling.employee.web;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.platform.error.ApiException;
import com.bsr.scheduling.employee.application.EmployeeContactPersistence.Contact;
import com.bsr.scheduling.employee.application.EmployeeContactService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
public class EmployeeContactController {
    private final EmployeeContactService service;
    private final AccountAuthentication accounts;
    public EmployeeContactController(EmployeeContactService service,AccountAuthentication accounts) { this.service=service; this.accounts=accounts; }
    @GetMapping java.util.List<ChoiceView> choices(@RequestParam String storeId,Authentication auth) {
        return service.choices(actor(auth),id(storeId)).stream().map(c -> new ChoiceView(Long.toString(c.employeeId()),c.employeeNumber(),c.fullName())).toList();
    }
    @GetMapping("/{employeeId}/contact") View read(@PathVariable String employeeId,@RequestParam String storeId,Authentication auth) {
        return View.from(service.read(actor(auth),id(storeId),id(employeeId)));
    }
    @PostMapping("/{employeeId}/contact") View update(@PathVariable String employeeId,@RequestBody Input input,Authentication auth) {
        return View.from(service.update(actor(auth),id(input.storeId()),id(employeeId),input.address(),input.note()));
    }
    private ActorContext actor(Authentication auth) { return accounts.loadActor(((BsrAccountPrincipal)auth.getPrincipal()).accountId()); }
    private static long id(String value) {
        if(value!=null && value.matches("[1-9][0-9]{0,18}")) try { return Long.parseLong(value); } catch(NumberFormatException ignored) { }
        throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","A positive identifier is required.");
    }
    record Input(String storeId,String address,String note) { }
    record ChoiceView(String employeeId,String employeeNumber,String fullName) { }
    record View(String employeeId,String employeeNumber,String fullName,String email,String phone,String address,String note) {
        static View from(Contact c) { return new View(Long.toString(c.employeeId()),c.employeeNumber(),c.fullName(),c.email(),c.phone(),c.address(),c.note()); }
    }
}
