package com.bsr.rental.application;

import com.bsr.customerrequest.api.BillingCustomerLookup;
import com.bsr.identity.api.ActorContext;
import com.bsr.rental.domain.ExternalTransactionResult;
import com.bsr.rental.domain.RentalContractStatus;
import com.bsr.storetransfer.store.api.StoreAccess;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {
    private static final BigDecimal ZERO=new BigDecimal("0.00");
    private final RentalService service;
    private final RentalPersistence rentals;
    private final TransactionPersistence transactions;
    private final BillingCustomerLookup customers;
    private final StoreAccess stores;
    public InvoiceService(RentalService service,RentalPersistence rentals,TransactionPersistence transactions,
            BillingCustomerLookup customers,StoreAccess stores) {
        this.service=service; this.rentals=rentals; this.transactions=transactions; this.customers=customers; this.stores=stores;
    }

    // The short root lock keeps fee/state/transaction totals from straddling a concurrent settlement.
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Invoice read(ActorContext actor,long contractId) {
        service.read(actor,contractId);
        rentals.lock(contractId).orElseThrow();
        var stored=service.read(actor,contractId);
        var contract=stored.contract();
        BigDecimal paid=ZERO,refunded=ZERO,pendingPayment=ZERO,pendingRefund=ZERO;
        var balances=new ArrayList<ChargeBalance>();
        boolean confirmed=contract.status()!=RentalContractStatus.DRAFT
                && contract.status()!=RentalContractStatus.PENDING_CONFIRMATION
                && contract.status()!=RentalContractStatus.CANCELLED;
        for(var charge:stored.charges()) {
            var history=transactions.forCharge(charge.id());
            var succeeded=history.stream().filter(t->t.result().status()==ExternalTransactionResult.Status.SUCCEEDED)
                    .map(t->t.result().amount()).reduce(ZERO,BigDecimal::add);
            var outstanding=charge.amount().subtract(succeeded).max(ZERO);
            boolean refund=charge.type().equals("REFUND");
            if(refund) { refunded=refunded.add(succeeded); pendingRefund=pendingRefund.add(outstanding); }
            else { paid=paid.add(succeeded); pendingPayment=pendingPayment.add(outstanding); }
            balances.add(new ChargeBalance(charge,outstanding,confirmed && !refund && outstanding.signum()>0,history));
        }
        var customer=customers.find(contract.customerId()).orElseThrow();
        var store=stores.findById(contract.serviceStoreId()).orElseThrow();
        return new Invoice(stored,customer.fullName(),customer.customerNumber(),store.storeName(),paid,refunded,
                pendingPayment,pendingRefund,List.copyOf(balances));
    }
    public record Invoice(RentalPersistence.StoredContract contract,String customerName,String customerNumber,String storeName,
            BigDecimal totalPaid,BigDecimal totalRefunded,BigDecimal outstandingPayment,BigDecimal outstandingRefund,List<ChargeBalance> charges) { }
    public record ChargeBalance(RentalPersistence.Charge charge,BigDecimal outstandingAmount,boolean canSimulatePayment,
            List<TransactionPersistence.RecordedTransaction> transactions) { }
}
