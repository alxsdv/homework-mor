package org.example.payments.core.ports.in;

import org.example.payments.core.domain.Payment;

public interface RecordSettledPaymentUseCase {
    Payment recordSettledPayment(RecordSettledPaymentCommand recordSettledPaymentCommand);
}
