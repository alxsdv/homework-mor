package org.example.payments.core.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

public record Payment(
        PaymentId paymentId,
        String externalId,
        LocalDate paymentDate,
        String merchantId,
        Customer customer,
        Currency currency,
        BigDecimal totalAmount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal netAmount) {}
