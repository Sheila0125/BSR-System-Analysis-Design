package com.bsr.customerrequest.api;

import java.util.Optional;

/** Minimal billing identity; caller must authorize access to its contract first. */
public interface BillingCustomerLookup {
    Optional<Customer> find(long customerId);
    record Customer(String customerNumber,String fullName) { }
}
