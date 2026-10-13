package org.example.payments.core.ports.out;

import org.example.payments.core.domain.RefundId;

public class DuplicateRefundIdException extends RuntimeException {
    private final RefundId refundId;

    public DuplicateRefundIdException(RefundId refundId) {
        super("Refund ID already exists: " + refundId.value());
        this.refundId = refundId;
    }

    public RefundId refundId() {
        return refundId;
    }
}
