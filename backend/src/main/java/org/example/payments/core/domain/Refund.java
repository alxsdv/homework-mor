package org.example.payments.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * A recorded refund of a settled payment. merchantId, taxCountry, currency, taxRate and feeRate are copied from
 * the payment, so a refund reads on its own. fullRefund means it was requested without an amount.
 */
public record Refund(
        RefundId refundId,
        PaymentId paymentId,
        String merchantId,
        String externalId,
        Instant refundedAt,
        boolean fullRefund,
        String taxCountry,
        Currency currency,
        BigDecimal taxRate,
        BigDecimal feeRate,
        BigDecimal totalAmount,
        BigDecimal taxAmount,
        BigDecimal netAmount,
        BigDecimal feeAmount,
        BigDecimal merchantAmount) {}
