package org.example.payouts.core.services;

import org.example.payouts.core.domain.Payout;
import org.example.payouts.core.ports.in.PayoutConflictException;
import org.example.payouts.core.ports.in.PayoutExceedsAvailableException;
import org.example.payouts.core.ports.in.RequestPayoutCommand;
import org.example.payouts.core.ports.in.RequestPayoutUseCase;
import org.example.payouts.core.ports.in.UnknownPayoutMerchantException;
import org.example.payouts.core.ports.out.DuplicatePayoutExternalIdException;
import org.example.payouts.core.ports.out.DuplicatePayoutIdException;
import org.example.payouts.core.ports.out.InsufficientAvailableBalanceException;
import org.example.payouts.core.ports.out.MerchantDirectory;
import org.example.payouts.core.ports.out.PayoutLedger;
import org.example.payouts.core.ports.out.PayoutsRepository;
import org.example.shared.transactions.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class RequestPayoutUseCaseImpl implements RequestPayoutUseCase {

    private static final int MAX_ATTEMPTS = 3;

    private final Logger logger = LoggerFactory.getLogger(RequestPayoutUseCaseImpl.class);
    private final PayoutsRepository payoutsRepository;
    private final MerchantDirectory merchantDirectory;
    private final PayoutLedger payoutLedger;
    private final TransactionRunner transactionRunner;

    public RequestPayoutUseCaseImpl(PayoutsRepository payoutsRepository, MerchantDirectory merchantDirectory,
                                    PayoutLedger payoutLedger, TransactionRunner transactionRunner) {
        this.payoutsRepository = payoutsRepository;
        this.merchantDirectory = merchantDirectory;
        this.payoutLedger = payoutLedger;
        this.transactionRunner = transactionRunner;
    }

    private static boolean matches(Payout payout, RequestPayoutCommand command) {
        return payout.currency().equals(command.currency())
               && payout.amount().compareTo(command.amount()) == 0
               && payout.requestedAt().equals(command.requestedAt());
    }

    @Override
    public Payout requestPayout(RequestPayoutCommand command) {
        logger.info("Request a payout for merchant ID {}", command.merchantId());
        logger.info("Request: {}", command);
        // Retry the whole transaction: after a unique-key violation the database aborts the current one.
        for (int attempt = 1; ; attempt++) {
            try {
                return transactionRunner.inTransaction(() -> findOrAddPayout(command));
            } catch (InsufficientAvailableBalanceException e) {
                // Thrown out of the transaction, so the payout row inserted before the hold is rolled back too.
                throw new PayoutExceedsAvailableException(command.merchantId(), command.currency(),
                                                          command.amount(), e.available());
            } catch (DuplicatePayoutIdException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                logger.warn("Generated payout ID {} already exists, retrying (attempt {}/{})",
                            e.payoutId().value(), attempt, MAX_ATTEMPTS);
            } catch (DuplicatePayoutExternalIdException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                logger.info("Payout {} for merchant {} was requested by a concurrent request, "
                            + "retrying to return it (attempt {}/{})",
                            e.externalId(), e.merchantId(), attempt, MAX_ATTEMPTS);
            }
        }
    }

    private Payout findOrAddPayout(RequestPayoutCommand command) {
        Optional<Payout> existing =
                payoutsRepository.findByMerchantAndExternalId(command.merchantId(), command.externalId());
        if (existing.isPresent()) {
            if (!matches(existing.get(), command)) {
                throw new PayoutConflictException(command.merchantId(), command.externalId());
            }
            return existing.get();
        }

        if (!merchantDirectory.exists(command.merchantId())) {
            throw new UnknownPayoutMerchantException(command.merchantId());
        }
        Payout payout = payoutsRepository.addPayout(Payout.requested(
                payoutsRepository.nextId(), command.merchantId(), command.externalId(), command.currency(),
                command.amount(), command.requestedAt()));
        // Last step on purpose: the merchant-account lock taken here is held until commit, and the funds check
        // made under it can't go stale before then.
        payoutLedger.hold(payout);
        return payout;
    }
}
