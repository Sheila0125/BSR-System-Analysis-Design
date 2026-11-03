package com.bsr.repair.web;

import static com.bsr.repair.web.RepairBoundary.call;
import static com.bsr.repair.web.RepairBoundary.date;
import static com.bsr.repair.web.RepairBoundary.decimal;
import static com.bsr.repair.web.RepairBoundary.id;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.repair.application.RepairTaskService;
import com.bsr.repair.application.RepairViews;
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
@RequestMapping("/api/repair-tasks")
public class RepairTaskController {

    private final RepairTaskService service;
    private final AccountAuthentication accounts;

    public RepairTaskController(RepairTaskService service, AccountAuthentication accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @GetMapping
    List<TaskView> employeeTasks(@RequestParam String storeId, Authentication auth) {
        return call(() -> service.employeeTasks(actor(auth), id(storeId)).stream()
                .map(TaskView::from).toList());
    }

    @GetMapping("/{taskId}")
    TaskView employeeTask(@PathVariable String taskId, Authentication auth) {
        return call(() -> TaskView.from(service.employeeTask(actor(auth), id(taskId))));
    }

    @GetMapping("/{taskId}/labors")
    List<LaborView> employeeLabors(@PathVariable String taskId, Authentication auth) {
        return call(() -> service.employeeLabors(actor(auth), id(taskId)).stream()
                .map(LaborView::from).toList());
    }

    @GetMapping("/{taskId}/charge")
    ChargeView employeeCharge(@PathVariable String taskId, Authentication auth) {
        return call(() -> ChargeView.from(service.employeeCharge(actor(auth), id(taskId))));
    }

    @PostMapping
    TaskView create(@RequestBody Creation body, Authentication auth) {
        return call(() -> TaskView.from(service.create(actor(auth), id(body.storeId()),
                id(body.equipmentId()), body.faultDescription(),
                body.contractorId() == null ? null : id(body.contractorId()))));
    }

    @PostMapping("/{taskId}/assign")
    TaskView assign(@PathVariable String taskId, @RequestBody Assignment body, Authentication auth) {
        return call(() -> TaskView.from(service.assign(actor(auth), id(taskId),
                id(body.contractorId()))));
    }

    @PostMapping("/{taskId}/accept")
    TaskView accept(@PathVariable String taskId, Authentication auth) {
        return call(() -> TaskView.from(service.accept(actor(auth), id(taskId))));
    }

    @PostMapping("/{taskId}/reject")
    TaskView reject(@PathVariable String taskId, @RequestBody Rejection body, Authentication auth) {
        return call(() -> TaskView.from(service.reject(actor(auth), id(taskId), body.reason())));
    }

    @PostMapping("/{taskId}/confirm-charge")
    TaskView confirmCharge(@PathVariable String taskId, Authentication auth) {
        return call(() -> TaskView.from(service.confirmCharge(actor(auth), id(taskId))));
    }

    @GetMapping("/mine")
    List<TaskView> mine(@RequestParam String view, Authentication auth) {
        return call(() -> service.contractorMine(actor(auth), view).stream()
                .map(TaskView::from).toList());
    }

    @GetMapping("/mine/charges")
    List<LaborView> feeHistory(Authentication auth) {
        return call(() -> service.contractorFeeHistory(actor(auth)).stream()
                .map(LaborView::from).toList());
    }

    @GetMapping("/mine/{taskId}")
    TaskView myTask(@PathVariable String taskId, Authentication auth) {
        return call(() -> TaskView.from(service.contractorTask(actor(auth), id(taskId))));
    }

    @GetMapping("/mine/{taskId}/labors")
    List<LaborView> myLabors(@PathVariable String taskId, Authentication auth) {
        return call(() -> service.contractorTaskLabors(actor(auth), id(taskId)).stream()
                .map(LaborView::from).toList());
    }

    @PostMapping("/mine/{taskId}/start")
    TaskView start(@PathVariable String taskId, Authentication auth) {
        return call(() -> TaskView.from(service.start(actor(auth), id(taskId))));
    }

    @PostMapping("/mine/{taskId}/labor")
    LaborView labor(@PathVariable String taskId, @RequestBody LaborInput body, Authentication auth) {
        return call(() -> LaborView.from(service.addLabor(actor(auth), id(taskId),
                date(body.repairDate()), decimal(body.durationHours()),
                body.handlingResult(), body.note())));
    }

    @PostMapping("/mine/{taskId}/submit")
    TaskView submit(@PathVariable String taskId, Authentication auth) {
        return call(() -> TaskView.from(service.submit(actor(auth), id(taskId))));
    }

    private ActorContext actor(Authentication auth) {
        return accounts.loadActor(((BsrAccountPrincipal) auth.getPrincipal()).accountId());
    }

    record Creation(String storeId, String equipmentId, String faultDescription,
            String contractorId) { }
    record Assignment(String contractorId) { }
    record Rejection(String reason) { }
    record LaborInput(String repairDate, String durationHours, String handlingResult,
            String note) { }

    record TaskView(String taskId, String number, String equipmentId, String serviceStoreId,
            String currentContractorId, String requestDate, String faultDescription,
            String status, String acceptanceResult, String acceptedAt) {
        static TaskView from(RepairViews.Task task) {
            return new TaskView(Long.toString(task.id()), task.number(),
                    Long.toString(task.equipmentId()), Long.toString(task.serviceStoreId()),
                    task.currentContractorId() == null ? null : task.currentContractorId().toString(),
                    task.requestDate().toString(), task.faultDescription(), task.status().name(),
                    task.acceptanceResult() == null ? null : task.acceptanceResult().name(),
                    task.acceptedAt() == null ? null : task.acceptedAt() + "Z");
        }
    }

    record LaborView(String laborId, String taskId, String contractorId,
            String repairDate, String durationHours, String handlingResult, String note,
            String repairRateId, String hourlyRateSnapshot, String laborAmount,
            String submittedAt) {
        static LaborView from(RepairViews.Labor labor) {
            return new LaborView(Long.toString(labor.id()), Long.toString(labor.taskId()),
                    Long.toString(labor.contractorId()), labor.repairDate().toString(),
                    labor.durationHours().toPlainString(), labor.handlingResult(), labor.note(),
                    labor.repairRateId() == null ? null : labor.repairRateId().toString(),
                    labor.hourlyRateSnapshot() == null ? null : labor.hourlyRateSnapshot().toPlainString(),
                    labor.laborAmount() == null ? null : labor.laborAmount().toPlainString(),
                    labor.submittedAt() == null ? null : labor.submittedAt() + "Z");
        }
    }

    record ChargeView(String chargeId, String taskId, String totalHours, String serviceAmount,
            String status, String confirmedByEmployeeId, String confirmedAt) {
        static ChargeView from(RepairViews.Charge charge) {
            if (charge == null) {
                return null;
            }
            return new ChargeView(Long.toString(charge.id()), Long.toString(charge.taskId()),
                    charge.totalHours().toPlainString(), charge.serviceAmount().toPlainString(),
                    charge.status(), charge.confirmedByEmployeeId() == null
                            ? null : charge.confirmedByEmployeeId().toString(),
                    charge.confirmedAt() == null ? null : charge.confirmedAt() + "Z");
        }
    }
}
