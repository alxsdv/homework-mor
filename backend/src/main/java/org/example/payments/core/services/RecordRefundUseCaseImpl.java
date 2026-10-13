package org.example.payments.core.services;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.Refund;
import org.example.payments.core.domain.RefundAmounts;
import org.example.payments.core.domain.RefundId;
import org.example.payments.core.ports.in.InvalidPaymentException;
import org.example.payments.core.ports.in.PaymentNotFoundException;
import org.example.payments.core.ports.in.RecordRefundCommand;
import org.example.payments.core.ports.in.RecordRefundUseCase;
import org.example.payments.core.ports.in.RefundConflictException;
import org.example.payments.core.ports.in.RefundExceedsRefundableException;
import org.example.payments.core.ports.out.DuplicateRefundExternalIdException;
import org.example.payments.core.ports.out.DuplicateRefundIdException;
import org.example.payments.core.ports.out.PaymentsRepository;
import org.example.payments.core.ports.out.RefundsRepository;
import org.example.payments.core.ports.out.SettlementLedger;
import org.example.shared.transactions.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Optional;

public class RecordRefundUseCaseImpl implements RecordRefundUseCase {

    private static final int MAX_ATTEMPTS = 3;

    private final Logger logger = LoggerFactory.getLogger(RecordRefundUseCaseImpl.class);
    private final PaymentsRepository paymentsRepository;
    private final RefundsRepository refundsRepository;
    private final SettlementLedger settlementLedger;
    private final TransactionRunner transactionRunner;

    public RecordRefundUseCaseImpl(PaymentsRepository paymentsRepository,
                                   RefundsRepository refundsRepository,
                                   SettlementLedger settlementLedger,
                                   TransactionRunner transactionRunner) {
        this.paymentsRepository = paymentsRepository;
        this.refundsRepository = refundsRepository;
        this.settlementLedger = settlementLedger;
        this.transactionRunner = transactionRunner;
    }

    /** Same payment and refund time, and either the same amount or no amount both times. */
    private static boolean matches(Refund refund, RecordRefundCommand command) {
        BigDecimal requested = command.refundAmount();
        boolean sameAmount = requested == null
                ? refund.fullRefund()
                : !refund.fullRefund() && refund.totalAmount().compareTo(requested) == 0;
        return refund.paymentId().equals(command.paymentId())
               && refund.refundedAt().equals(command.refundedAt())
               && sameAmount;
    }

    private static void validateAgainst(Payment payment, RecordRefundCommand command) {
        if (command.refundedAt().isBefore(payment.settledAt())) {
            throw new InvalidPaymentException("refundedAt must not be before the payment's settledAt "
                                              + payment.settledAt() + ": " + command.refundedAt());
        }
        BigDecimal amount = command.refundAmount();
        int digits = payment.currency().getDefaultFractionDigits();
        if (amount != null && amount.stripTrailingZeros().scale() > digits) {
            throw new InvalidPaymentException("refundAmount has more decimal places than " + payment.currency()
                                              + " allows (" + digits + "): " + amount);
        }
    }

    private static Refund createRefund(RefundId refundId, RecordRefundCommand command, Payment payment,
                                       RefundAmounts amounts) {
        return new Refund(refundId, payment.paymentId(), payment.merchantId(), command.externalId(),
                          command.refundedAt(), command.refundAmount() == null, payment.taxCountry(),
                          payment.currency(), payment.taxRate(), payment.feeRate(), amounts.totalAmount(),
                          amounts.taxAmount(), amounts.netAmount(), amounts.feeAmount(), amounts.merchantAmount());
    }

    @Override
    public Refund recordRefund(RecordRefundCommand command) {
        logger.info("Record a refund of payment {}", command.paymentId().value());
        logger.info("Request: {}", command);
        // Retry the whole transaction: after a unique-key violation the database aborts the current one.
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionRunner.inTransaction(() -> findOrAddRefund(command));
            } catch (DuplicateRefundIdException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                logger.warn("Generated refund ID {} already exists, retrying (attempt {}/{})",
                            e.refundId().value(), attempt, MAX_ATTEMPTS);
            } catch (DuplicateRefundExternalIdException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                logger.info("Refund {} for merchant {} was recorded by a concurrent request, "
                            + "retrying to return it (attempt {}/{})",
                            e.externalId(), e.merchantId(), attempt, MAX_ATTEMPTS);
            }
        }
    }

    private Refund findOrAddRefund(RecordRefundCommand command) {
        // The row lock makes concurrent refunds of this payment take turns, so each sees the others' amounts.
        Payment payment = paymentsRepository.findByIdForUpdate(command.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(command.paymentId().value()));

        Optional<Refund> existing =
                refundsRepository.findByMerchantAndExternalId(payment.merchantId(), command.externalId());
        if (existing.isPresent()) {
            if (!matches(existing.get(), command)) {
                throw new RefundConflictException(payment.merchantId(), command.externalId());
            }
            return existing.get();
        }

        validateAgainst(payment, command);
        RefundAmounts refunded = refundsRepository.refundedSoFar(payment.paymentId(), payment.currency());
        BigDecimal refundable = payment.totalAmount().subtract(refunded.totalAmount());
        BigDecimal requested = command.refundAmount();
        BigDecimal amount = requested != null ? requested : refundable;
        if (refundable.signum() == 0 || amount.compareTo(refundable) > 0) {
            throw new RefundExceedsRefundableException(payment.paymentId(), amount, refundable);
        }

        RefundAmounts amounts = RefundAmounts.calculate(payment, refunded, amount);
        Refund refund = refundsRepository.addRefund(
                createRefund(refundsRepository.nextId(), command, payment, amounts));
        // Last step on purpose: the merchant-account lock taken here is held until commit.
        settlementLedger.recordRefund(refund);
        return refund;
    }
}
