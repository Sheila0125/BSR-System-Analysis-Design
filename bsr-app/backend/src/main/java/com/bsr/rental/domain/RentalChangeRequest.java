package com.bsr.rental.domain;

import java.time.LocalDateTime;

/** Submitted customer intent only; contains no final equipment or employee handling facts. */
public record RentalChangeRequest(String number,long contractId,Type type,long customerId,
        long requestedStoreId,LocalDateTime requestedAt,String note) {
    public enum Type { ADD, REMOVE, REPLACE, PARTIAL_RETURN }
    public RentalChangeRequest {
        if(number==null || number.isBlank() || number.length()>30 || contractId<=0 || type==null
                || customerId<=0 || requestedStoreId<=0 || requestedAt==null)
            throw new IllegalArgumentException("Complete trusted request identifiers and time are required");
        note=note==null || note.isBlank()?null:note.trim();
        if(note!=null && note.length()>500) throw new IllegalArgumentException("Request note must not exceed 500 characters");
    }
    public void requireRequestableContract(RentalContract contract,boolean allowEndedRefund) {
        if(contract.customerId()!=customerId || (contract.status()!=RentalContractStatus.ACTIVE
                && !(allowEndedRefund && contract.status()==RentalContractStatus.ENDED)))
            throw new IllegalStateException("Changes can only be requested for the customer's own active contract or an eligible ended refund");
    }
}
