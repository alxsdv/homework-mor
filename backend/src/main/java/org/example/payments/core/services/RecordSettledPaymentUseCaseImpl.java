package org.example.payments.core.services;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.ports.in.RecordSettledPaymentCommand;
import org.example.payments.core.ports.in.RecordSettledPaymentUseCase;
import org.example.payments.core.ports.out.DuplicatePaymentIdException;
import org.example.payments.core.ports.out.PaymentsRepository;
import org.example.payments.core.ports.out.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public class RecordSettledPaymentUseCaseImpl implements RecordSettledPaymentUseCase {

    private static final int MAX_PAYMENT_ID_ATTEMPTS = 3;

    private final Logger logger = LoggerFactory.getLogger(RecordSettledPaymentUseCaseImpl.class);
    private final PaymentsRepository paymentsRepository;
    private final TransactionRunner transactionRunner;

    public RecordSettledPaymentUseCaseImpl(PaymentsRepository paymentsRepository, TransactionRunner transactionRunner) {
        this.paymentsRepository = paymentsRepository;
        this.transactionRunner = transactionRunner;
    }

    private static Payment createPayment(PaymentId paymentId, RecordSettledPaymentCommand command) {
        BigDecimal totalAmount = command.totalAmount();
        BigDecimal taxRate = command.taxRate();
        BigDecimal netAmount = calculateNetAmount(totalAmount, taxRate, command.currency());
        BigDecimal taxAmount = totalAmount.subtract(netAmount);
        return new Payment(paymentId, command.externalId(), command.paymentDate(), command.merchantId(),
                           command.customer(),
                           command.currency(), totalAmount, taxRate, taxAmount, netAmount);
    }

    private static BigDecimal calculateNetAmount(BigDecimal totalAmount, BigDecimal taxRate, Currency currency) {
        Objects.requireNonNull(totalAmount, "Total amount cannot be null");
        Objects.requireNonNull(taxRate, "Tax rate cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");
        if (taxRate.signum() < 0) {
            throw new IllegalArgumentException("Tax rate cannot be negative: " + taxRate);
        }
        if (totalAmount.signum() < 0) {
            throw new IllegalArgumentException("Total amount cannot be negative: " + totalAmount);
        }
        return totalAmount.divide(BigDecimal.ONE.add(taxRate), currency.getDefaultFractionDigits(),
                                  RoundingMode.HALF_UP);
    }

    @Override
    public Payment recordSettledPayment(RecordSettledPaymentCommand recordSettledPaymentCommand) {
        logger.info("Record a settled payment for merchant ID {}", recordSettledPaymentCommand.merchantId());
        logger.info("Request: {}", recordSettledPaymentCommand);
        // Retry the whole transaction: after a unique-key violation the database aborts the current one.
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionRunner.inTransaction(() -> findOrAddPayment(recordSettledPaymentCommand));
            } catch (DuplicatePaymentIdException e) {
                if (attempt >= MAX_PAYMENT_ID_ATTEMPTS) {
                    throw e;
                }
                logger.warn("Generated payment ID {} already exists, retrying (attempt {}/{})",
                            e.paymentId().value(), attempt, MAX_PAYMENT_ID_ATTEMPTS);
            }
        }
    }

    private Payment findOrAddPayment(RecordSettledPaymentCommand recordSettledPaymentCommand) {
        var existingPayments = paymentsRepository.getPaymentsByMerchantAndExternalId(
                recordSettledPaymentCommand.merchantId(), recordSettledPaymentCommand.externalId());
        if (existingPayments.size() > 1) {
            throw new IllegalStateException("Found multiple conflicting payments with the same external ID!");
        }
        if (existingPayments.size() == 1) {
            return existingPayments.getFirst();
        }

        return paymentsRepository.addPayment(
                createPayment(paymentsRepository.nextId(), recordSettledPaymentCommand));
    }
}
