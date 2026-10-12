package org.example.payments.core.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * A recorded settled payment. feeRate and taxCountry are snapshots taken when it was recorded,
 * so later changes to the merchant's fee rate don't affect it.
 */
public record Payment(
        PaymentId paymentId,
        String externalId,
        Instant settledAt,
        String merchantId,
        Customer customer,
        String taxCountry,
        Currency currency,
        BigDecimal totalAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal netAmount,
        BigDecimal feeRate,
        BigDecimal feeAmount,
        BigDecimal merchantAmount) {}
