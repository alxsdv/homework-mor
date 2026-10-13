package org.example.payments.core.services;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.Refund;
import org.example.payments.core.ports.out.SettlementLedger;

import java.util.ArrayList;
import java.util.List;

class FakeSettlementLedger implements SettlementLedger {

    final List<Payment> recorded = new ArrayList<>();
    final List<Refund> refunds = new ArrayList<>();

    @Override
    public void recordSettlement(Payment payment) {
        recorded.add(payment);
    }

    @Override
    public void recordRefund(Refund refund) {
        refunds.add(refund);
    }
}
