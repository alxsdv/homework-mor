package org.example.payments.core.services;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.ports.out.SettlementLedger;

import java.util.ArrayList;
import java.util.List;

class FakeSettlementLedger implements SettlementLedger {

    final List<Payment> recorded = new ArrayList<>();

    @Override
    public void recordSettlement(Payment payment) {
        recorded.add(payment);
    }
}
