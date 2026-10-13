package org.example.payments.core.ports.in;

import org.example.payments.core.domain.PaymentId;

import java.math.BigDecimal;

/** The refund asks for more than the payment has left to refund. Maps to 409. */
public class RefundExceedsRefundableException extends RuntimeException {

    public RefundExceedsRefundableException(PaymentId paymentId, BigDecimal refundAmount, BigDecimal refundable) {
        super(refundable.signum() == 0
              ? "Payment " + paymentId.value() + " has nothing left to refund"
              : "Refund amount " + refundAmount.toPlainString() + " exceeds the refundable amount "
                + refundable.toPlainString() + " of payment " + paymentId.value());
    }
}
