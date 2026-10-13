package org.example.payments.core.ports.out;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.Refund;

/** Records in the ledger what settlements and refunds mean: what we owe the merchant, the tax authority, us. */
public interface SettlementLedger {
    void recordSettlement(Payment payment);

    /** The settlement in reverse, for the refunded share: cash back out, and less owed to everyone. */
    void recordRefund(Refund refund);
}
