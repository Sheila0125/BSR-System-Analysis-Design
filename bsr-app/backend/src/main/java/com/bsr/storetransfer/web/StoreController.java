package com.bsr.storetransfer.web;

import static com.bsr.storetransfer.web.StoreTransferBoundary.allowed;
import static com.bsr.storetransfer.web.StoreTransferBoundary.call;
import static com.bsr.storetransfer.web.StoreTransferBoundary.id;
import static com.bsr.storetransfer.web.StoreTransferBoundary.text;
import static com.bsr.storetransfer.web.StoreTransferBoundary.time;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.identity.api.ActorContext;
import com.bsr.storetransfer.store.api.StoreSnapshot;
import com.bsr.storetransfer.store.application.StoreAdministrationService;
import java.util.List;
import tools.jackson.databind.JsonNode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores")
public class StoreController {
    private final StoreAdministrationService service;
    private final AccountAuthentication accounts;

    public StoreController(StoreAdministrationService service, AccountAuthentication accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @GetMapping
    StoreView get(@RequestParam String storeId, Authentication auth) {
        return call(() -> StoreView.from(service.get(actor(auth), id(storeId))));
    }

    @GetMapping("/choices")
    List<StoreChoice> choices(Authentication auth) {
        return service.choices(actor(auth)).stream()
                .map(store -> new StoreChoice(Long.toString(store.storeId()), store.storeNumber(), store.storeName()))
                .toList();
    }

    @PostMapping
    StoreView create(@RequestBody JsonNode body, Authentication auth) {
        return call(() -> {
            allowed(body, "authorityStoreId", "storeNumber", "storeName", "address",
                    "openingTime", "closingTime");
            return StoreView.from(service.create(actor(auth), id(body.get("authorityStoreId"), "authorityStoreId"),
                    text(body.get("storeNumber"), "storeNumber", false),
                    text(body.get("storeName"), "storeName", false),
                    text(body.get("address"), "address", true),
                    time(body.get("openingTime"), "openingTime"),
                    time(body.get("closingTime"), "closingTime")));
        });
    }

    @PostMapping("/{storeId}/update")
    StoreView update(@PathVariable String storeId, @RequestBody JsonNode body, Authentication auth) {
        return call(() -> {
            allowed(body, "storeName", "address", "openingTime", "closingTime");
            return StoreView.from(service.update(actor(auth), id(storeId),
                    text(body.get("storeName"), "storeName", false),
                    text(body.get("address"), "address", true),
                    time(body.get("openingTime"), "openingTime"),
                    time(body.get("closingTime"), "closingTime")));
        });
    }

    private ActorContext actor(Authentication auth) {
        return accounts.loadActor(((BsrAccountPrincipal) auth.getPrincipal()).accountId());
    }

    record StoreChoice(String storeId, String storeNumber, String storeName) { }

    record StoreView(String storeId, String storeNumber, String storeName, String address,
            String businessStatus, String openingTime, String closingTime) {
        static StoreView from(StoreSnapshot store) {
            return new StoreView(Long.toString(store.storeId()), store.storeNumber(),
                    store.storeName(), store.address(), store.active() ? "ACTIVE" : "INACTIVE",
                    store.openingTime().toString(), store.closingTime().toString());
        }
    }
}
