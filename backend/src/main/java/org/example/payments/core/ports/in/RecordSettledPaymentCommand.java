package org.example.payments.core.ports.in;

import org.example.payments.core.domain.Customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

public record RecordSettledPaymentCommand(
        String externalId,
        LocalDate paymentDate,
        String merchantId,
        Customer customer,
        Currency currency,
        BigDecimal totalAmount,
        BigDecimal taxRate) {}
