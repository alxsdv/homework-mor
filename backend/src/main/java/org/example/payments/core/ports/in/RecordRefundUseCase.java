package org.example.payments.core.ports.in;

import org.example.payments.core.domain.Refund;

public interface RecordRefundUseCase {
    Refund recordRefund(RecordRefundCommand command);
}
