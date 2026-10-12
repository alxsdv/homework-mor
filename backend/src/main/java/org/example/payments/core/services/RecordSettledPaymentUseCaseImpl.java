package org.example.payments.core.services;

import org.example.payments.core.domain.Merchant;
import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentAmounts;
import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.ports.in.PaymentConflictException;
import org.example.payments.core.ports.in.RecordSettledPaymentCommand;
import org.example.payments.core.ports.in.RecordSettledPaymentUseCase;
import org.example.payments.core.ports.in.UnknownMerchantException;
import org.example.payments.core.ports.out.DuplicateExternalIdException;
import org.example.payments.core.ports.out.DuplicatePaymentIdException;
import org.example.payments.core.ports.out.MerchantsRepository;
import org.example.payments.core.ports.out.PaymentsRepository;
import org.example.payments.core.ports.out.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class RecordSettledPaymentUseCaseImpl implements RecordSettledPaymentUseCase {

    private static final int MAX_ATTEMPTS = 3;

    private final Logger logger = LoggerFactory.getLogger(RecordSettledPaymentUseCaseImpl.class);
    private final PaymentsRepository paymentsRepository;
    private final MerchantsRepository merchantsRepository;
    private final TransactionRunner transactionRunner;

    public RecordSettledPaymentUseCaseImpl(PaymentsRepository paymentsRepository,
                                           MerchantsRepository merchantsRepository,
                                           TransactionRunner transactionRunner) {
        this.paymentsRepository = paymentsRepository;
        this.merchantsRepository = merchantsRepository;
        this.transactionRunner = transactionRunner;
    }

    private static Payment createPayment(PaymentId paymentId, RecordSettledPaymentCommand command,
                                         Merchant merchant) {
        PaymentAmounts amounts = PaymentAmounts.calculate(command.totalAmount(), command.taxRate(),
                                                          merchant.feeRate(), command.currency());
        return new Payment(paymentId, command.externalId(), command.settledAt(), command.merchantId(),
                           command.customer(), command.customer().billingContact().countryCode(),
                           command.currency(), amounts.totalAmount(), command.taxRate(), amounts.taxAmount(),
                           amounts.netAmount(), merchant.feeRate(), amounts.feeAmount(), amounts.merchantAmount());
    }

    private static boolean matches(Payment payment, RecordSettledPaymentCommand command) {
        return payment.totalAmount().compareTo(command.totalAmount()) == 0
               && payment.currency().equals(command.currency())
               && payment.settledAt().equals(command.settledAt());
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
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                logger.warn("Generated payment ID {} already exists, retrying (attempt {}/{})",
                            e.paymentId().value(), attempt, MAX_ATTEMPTS);
            } catch (DuplicateExternalIdException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                logger.info("Payment {} for merchant {} was recorded by a concurrent request, "
                            + "retrying to return it (attempt {}/{})",
                            e.externalId(), e.merchantId(), attempt, MAX_ATTEMPTS);
            }
        }
    }

    private Payment findOrAddPayment(RecordSettledPaymentCommand command) {
        Optional<Payment> existing =
                paymentsRepository.findByMerchantAndExternalId(command.merchantId(), command.externalId());
        if (existing.isPresent()) {
            if (!matches(existing.get(), command)) {
                throw new PaymentConflictException(command.merchantId(), command.externalId());
            }
            return existing.get();
        }

        Merchant merchant = merchantsRepository.findById(command.merchantId())
                .orElseThrow(() -> new UnknownMerchantException(command.merchantId()));
        return paymentsRepository.addPayment(createPayment(paymentsRepository.nextId(), command, merchant));
    }
}
