package com.bsr.storetransfer.web;

import static com.bsr.storetransfer.web.StoreTransferBoundary.allowed;
import static com.bsr.storetransfer.web.StoreTransferBoundary.call;
import static com.bsr.storetransfer.web.StoreTransferBoundary.id;
import static com.bsr.storetransfer.web.StoreTransferBoundary.ids;
import static com.bsr.storetransfer.web.StoreTransferBoundary.text;
import static com.bsr.storetransfer.web.StoreTransferBoundary.value;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.storetransfer.transfer.application.TransferService;
import com.bsr.storetransfer.transfer.application.TransferViews;
import com.bsr.storetransfer.transfer.domain.TransferItem;
import com.bsr.storetransfer.transfer.domain.TransferType;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
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
@RequestMapping("/api/transfers")
public class TransferController {
    private final TransferService service;
    private final AccountAuthentication accounts;

    public TransferController(TransferService service, AccountAuthentication accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @GetMapping
    List<TransferView> list(@RequestParam String storeId, Authentication auth) {
        return call(() -> service.list(actor(auth), id(storeId)).stream()
                .map(TransferView::from).toList());
    }

    @GetMapping("/{transferId}")
    TransferView detail(@PathVariable String transferId, Authentication auth) {
        return call(() -> TransferView.from(service.detail(actor(auth), id(transferId))));
    }

    @PostMapping
    TransferView initiate(@RequestBody JsonNode body, Authentication auth) {
        return call(() -> {
            allowed(body, "sourceStoreId", "destinationStoreId", "transferType", "equipmentIds");
            return TransferView.from(service.initiate(actor(auth),
                    id(body.get("sourceStoreId"), "sourceStoreId"),
                    id(body.get("destinationStoreId"), "destinationStoreId"),
                    value(TransferType.class, body.get("transferType"), "transferType"),
                    ids(body.get("equipmentIds"), "equipmentIds")));
        });
    }

    @PostMapping("/{transferId}/approve")
    TransferView approve(@PathVariable String transferId, @RequestBody JsonNode body,
            Authentication auth) {
        return call(() -> {
            allowed(body);
            return TransferView.from(service.approve(actor(auth), id(transferId)));
        });
    }

    @PostMapping("/{transferId}/items/{itemId}/dispatch")
    TransferView dispatch(@PathVariable String transferId, @PathVariable String itemId,
            @RequestBody JsonNode body, Authentication auth) {
        return call(() -> {
            allowed(body);
            return TransferView.from(service.dispatch(actor(auth), id(transferId), id(itemId)));
        });
    }

    @PostMapping("/{transferId}/items/{itemId}/receive")
    TransferView receive(@PathVariable String transferId, @PathVariable String itemId,
            @RequestBody JsonNode body, Authentication auth) {
        return call(() -> {
            allowed(body);
            return TransferView.from(service.receive(actor(auth), id(transferId), id(itemId)));
        });
    }

    @PostMapping("/{transferId}/items/{itemId}/exception")
    TransferView exception(@PathVariable String transferId, @PathVariable String itemId,
            @RequestBody JsonNode body, Authentication auth) {
        return call(() -> {
            allowed(body, "phase", "note");
            return TransferView.from(service.reportException(actor(auth), id(transferId), id(itemId),
                    value(TransferItem.Phase.class, body.get("phase"), "phase"),
                    text(body.get("note"), "note", false)));
        });
    }

    private ActorContext actor(Authentication auth) {
        return accounts.loadActor(((BsrAccountPrincipal) auth.getPrincipal()).accountId());
    }

    record TransferView(String transferId, String transferNumber, String transferType,
            String sourceStoreId, String destinationStoreId, String initiatedByEmployeeId,
            String initiatedAt, String approvedByEmployeeId, String approvedAt,
            String completedAt, String status, List<ItemView> items) {
        static TransferView from(TransferViews.TransferRecord record) {
            var transfer = record.aggregate();
            return new TransferView(Long.toString(record.id()), transfer.number(),
                    transfer.type().name(), Long.toString(transfer.sourceStoreId()),
                    Long.toString(transfer.destinationStoreId()), Long.toString(transfer.initiatedBy()),
                    time(transfer.initiatedAt()), optionalId(transfer.approvedBy()),
                    time(transfer.approvedAt()), time(transfer.completedAt()),
                    transfer.status().name(), transfer.items().stream().map(item ->
                            ItemView.from(record.itemIds().get(item.equipmentId()), item)).toList());
        }
    }

    record ItemView(String itemId, String equipmentId, String dispatchResult,
            String dispatchedByEmployeeId, String dispatchedAt, String receiptResult,
            String receivedByEmployeeId, String receivedAt, String exceptionNote) {
        static ItemView from(long itemId, TransferItem item) {
            return new ItemView(Long.toString(itemId), Long.toString(item.equipmentId()),
                    item.dispatchResult().name(), optionalId(item.dispatchedBy()), time(item.dispatchedAt()),
                    item.receiptResult().name(), optionalId(item.receivedBy()), time(item.receivedAt()),
                    item.exceptionNote());
        }
    }

    private static String optionalId(Long value) {
        return value == null ? null : value.toString();
    }

    private static String time(LocalDateTime value) {
        return value == null ? null : value + "Z";
    }
}
