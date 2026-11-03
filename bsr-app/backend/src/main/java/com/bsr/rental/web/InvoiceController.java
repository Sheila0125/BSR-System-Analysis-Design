package com.bsr.rental.web;

import static com.bsr.rental.web.RentalBoundary.*;

import com.bsr.bootstrap.security.BsrAccountPrincipal;
import com.bsr.identity.api.AccountAuthentication;
import com.bsr.rental.application.InvoiceService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts/{contractId}/invoice")
public class InvoiceController {
    private final InvoiceService service;
    private final AccountAuthentication accounts;
    public InvoiceController(InvoiceService service,AccountAuthentication accounts) { this.service=service; this.accounts=accounts; }
    @GetMapping
    InvoiceView read(@PathVariable String contractId,Authentication auth) {
        return call(()->InvoiceView.from(service.read(accounts.loadActor(((BsrAccountPrincipal)auth.getPrincipal()).accountId()),id(contractId))));
    }
    record InvoiceView(String invoiceNumber,String documentType,String currency,String contractId,String contractNumber,
            String customerId,String customerName,String customerNumber,String serviceStoreId,String storeName,String contractDate,
            String rentalStartDate,int rentalDays,String contractStatus,String amountDue,String totalPaid,String totalRefunded,
            String outstandingPayment,String outstandingRefund,List<RentalController.ItemView> items,List<ChargeView> charges) {
        static InvoiceView from(InvoiceService.Invoice invoice) {
            var stored=invoice.contract(); var c=stored.contract();
            return new InvoiceView("INV-"+c.contractNumber(),"COURSE_DEMO_INVOICE","NOK",Long.toString(stored.id()),c.contractNumber(),
                    Long.toString(c.customerId()),invoice.customerName(),invoice.customerNumber(),Long.toString(c.serviceStoreId()),invoice.storeName(),
                    c.contractDate().toString(),c.rentalStartDate().toString(),c.rentalDays(),c.status().name(),c.amountDue().toPlainString(),
                    invoice.totalPaid().toPlainString(),invoice.totalRefunded().toPlainString(),invoice.outstandingPayment().toPlainString(),
                    invoice.outstandingRefund().toPlainString(),stored.items().stream().map(RentalController.ItemView::from).toList(),
                    invoice.charges().stream().map(ChargeView::from).toList());
        }
    }
    record ChargeView(String chargeId,String type,String amount,String status,String outstandingAmount,boolean canSimulatePayment,
            String createdAt,List<TransactionController.View> transactions) {
        static ChargeView from(InvoiceService.ChargeBalance balance) {
            var c=balance.charge();
            return new ChargeView(Long.toString(c.id()),c.type(),c.amount().toPlainString(),c.status(),balance.outstandingAmount().toPlainString(),
                    balance.canSimulatePayment(),c.createdAt()+"Z",balance.transactions().stream().map(TransactionController.View::from).toList());
        }
    }
}
