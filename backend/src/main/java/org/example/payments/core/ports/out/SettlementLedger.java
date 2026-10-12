package org.example.payments.core.ports.out;

import org.example.payments.core.domain.Payment;

/** Records in the ledger what a newly settled payment means: what we owe the merchant, the tax authority, us. */
public interface SettlementLedger {
    void recordSettlement(Payment payment);
}
