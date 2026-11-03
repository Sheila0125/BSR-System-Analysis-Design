package com.bsr.scheduling.shift.web;

import static com.bsr.scheduling.shift.web.ShiftBoundary.allowed;
import static com.bsr.scheduling.shift.web.ShiftBoundary.call;
import static com.bsr.scheduling.shift.web.ShiftBoundary.date;
import static com.bsr.scheduling.shift.web.ShiftBoundary.id;
import static com.bsr.scheduling.shift.web.ShiftBoundary.text;
import static com.bsr.scheduling.shift.web.ShiftBoundary.time;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.scheduling.shift.application.ShiftService;
import com.bsr.scheduling.shift.domain.Shift;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private final ShiftService service;
    private final AccountAuthentication accounts;

    public ShiftController(ShiftService service, AccountAuthentication accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @PostMapping
    ShiftView create(@RequestBody JsonNode body, Authentication auth) {
        return call(() -> {
            allowedSchedule(body);
            return ShiftView.from(service.create(actor(auth), date(body.get("shiftDate"), "shiftDate"),
                    id(body.get("storeId"), "storeId"), id(body.get("employeeId"), "employeeId"),
                    time(body.get("startTime"), "startTime"), time(body.get("endTime"), "endTime")));
        });
    }

    @PostMapping("/{shiftId}/adjust")
    ShiftView adjust(@PathVariable String shiftId, @RequestBody JsonNode body, Authentication auth) {
        return call(() -> {
            allowedSchedule(body);
            return ShiftView.from(service.adjust(actor(auth), id(shiftId),
                    date(body.get("shiftDate"), "shiftDate"), id(body.get("storeId"), "storeId"),
                    id(body.get("employeeId"), "employeeId"), time(body.get("startTime"), "startTime"),
                    time(body.get("endTime"), "endTime")));
        });
    }

    @PostMapping("/{shiftId}/cancel")
    ShiftView cancel(@PathVariable String shiftId, @RequestBody JsonNode body, Authentication auth) {
        return call(() -> {
            allowed(body, "reason");
            return ShiftView.from(service.cancel(actor(auth), id(shiftId), text(body.get("reason"), "reason")));
        });
    }

    @GetMapping
    List<ShiftView> list(@RequestParam String storeId, Authentication auth) {
        return call(() -> service.storeShifts(actor(auth), id(storeId)).stream().map(ShiftView::from).toList());
    }

    @GetMapping("/{shiftId}")
    ShiftView detail(@PathVariable String shiftId, Authentication auth) {
        return call(() -> ShiftView.from(service.find(actor(auth), id(shiftId))));
    }

    private ActorContext actor(Authentication auth) {
        return accounts.loadActor(((BsrAccountPrincipal) auth.getPrincipal()).accountId());
    }

    private static void allowedSchedule(JsonNode body) {
        allowed(body, "shiftDate", "storeId", "employeeId", "startTime", "endTime");
    }

    record ShiftView(String shiftId, String shiftNumber, String shiftDate, String storeId,
            String employeeId, String startTime, String endTime, String status,
            String changedByEmployeeId, String changedAt, String cancellationReason) {
        static ShiftView from(Shift shift) {
            return new ShiftView(shift.id().toString(), shift.number(), shift.date().toString(),
                    Long.toString(shift.storeId()), Long.toString(shift.employeeId()),
                    shift.start().format(HH_MM), shift.end().format(HH_MM), shift.status().name(),
                    Long.toString(shift.changedByEmployeeId()), utc(shift.changedAt()),
                    shift.cancellationReason());
        }

        private static String utc(LocalDateTime value) {
            return value + "Z";
        }
    }
}
