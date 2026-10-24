package org.example.payouts.adapters.out;

import org.example.jooq.tables.records.PayoutsRecord;
import org.example.payouts.core.domain.Payout;
import org.example.payouts.core.domain.PayoutId;
import org.example.payouts.core.domain.PayoutStatus;
import org.example.payouts.core.ports.out.DuplicatePayoutExternalIdException;
import org.example.payouts.core.ports.out.DuplicatePayoutIdException;
import org.example.payouts.core.ports.out.PayoutsRepository;
import org.example.shared.persistence.UniqueViolations;
import org.example.shared.utils.PublicIds;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;

import static org.example.jooq.Tables.PAYOUTS;

@Repository
public class PayoutsRepositoryImpl implements PayoutsRepository {

    // Constraint names are set explicitly in V5__payouts.sql.
    static final String PUBLIC_ID_CONSTRAINT = "payouts_public_id_key";
    static final String MERCHANT_EXTERNAL_ID_CONSTRAINT = "payouts_merchant_id_external_id_key";

    private final Logger logger = LoggerFactory.getLogger(PayoutsRepositoryImpl.class);
    private final DSLContext dsl;

    public PayoutsRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    private static Payout toDomain(PayoutsRecord r) {
        Currency currency = Currency.getInstance(r.getCurrency());
        OffsetDateTime completedAt = r.getCompletedAt();
        return new Payout(
                new PayoutId(r.getPublicId()),
                r.getMerchantId(),
                r.getExternalId(),
                currency,
                // numeric(19,4) comes back with 4 decimals; trim to the currency's (exact, only zeros are dropped).
                r.getAmount().setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY),
                r.getRequestedAt().toInstant(),
                PayoutStatus.valueOf(r.getStatus()),
                completedAt == null ? null : completedAt.toInstant());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    @Override
    public PayoutId nextId() {
        return new PayoutId(PublicIds.generate("PO", 10));
    }

    @Override
    public Optional<Payout> findByMerchantAndExternalId(String merchantId, String externalId) {
        logger.info("Getting Payout by Merchant ID \"{}\" and External ID \"{}\"", merchantId, externalId);
        return dsl.selectFrom(PAYOUTS)
                .where(PAYOUTS.MERCHANT_ID.eq(merchantId))
                .and(PAYOUTS.EXTERNAL_ID.eq(externalId))
                .fetchOptional()
                .map(PayoutsRepositoryImpl::toDomain);
    }

    @Override
    public Optional<Payout> findByIdForUpdate(PayoutId payoutId) {
        logger.info("Locking payout {}", payoutId.value());
        return dsl.selectFrom(PAYOUTS)
                .where(PAYOUTS.PUBLIC_ID.eq(payoutId.value()))
                .forUpdate()
                .fetchOptional()
                .map(PayoutsRepositoryImpl::toDomain);
    }

    @Override
    public Payout addPayout(Payout payout) {
        logger.info("Adding payout {} for merchant ID {}", payout.payoutId().value(), payout.merchantId());
        Instant completedAt = payout.completedAt();
        try {
            dsl.insertInto(PAYOUTS)
                    .set(PAYOUTS.PUBLIC_ID, payout.payoutId().value())
                    .set(PAYOUTS.MERCHANT_ID, payout.merchantId())
                    .set(PAYOUTS.EXTERNAL_ID, payout.externalId())
                    .set(PAYOUTS.CURRENCY, payout.currency().getCurrencyCode())
                    .set(PAYOUTS.AMOUNT, payout.amount())
                    .set(PAYOUTS.REQUESTED_AT, utc(payout.requestedAt()))
                    .set(PAYOUTS.STATUS, payout.status().name())
                    .set(PAYOUTS.COMPLETED_AT, completedAt == null ? null : utc(completedAt))
                    .execute();
        } catch (DuplicateKeyException e) {
            String constraint = UniqueViolations.constraintName(e);
            if (PUBLIC_ID_CONSTRAINT.equals(constraint)) {
                throw new DuplicatePayoutIdException(payout.payoutId());
            }
            if (MERCHANT_EXTERNAL_ID_CONSTRAINT.equals(constraint)) {
                throw new DuplicatePayoutExternalIdException(payout.merchantId(), payout.externalId());
            }
            throw e;
        }
        return payout;
    }

    @Override
    public void updateCompleted(Payout payout) {
        Instant completedAt = payout.completedAt();
        if (completedAt == null) {
            throw new IllegalArgumentException("Payout " + payout.payoutId().value() + " has not ended");
        }
        logger.info("Payout {} ended {}", payout.payoutId().value(), payout.status());
        int updated = dsl.update(PAYOUTS)
                .set(PAYOUTS.STATUS, payout.status().name())
                .set(PAYOUTS.COMPLETED_AT, utc(completedAt))
                .where(PAYOUTS.PUBLIC_ID.eq(payout.payoutId().value()))
                .and(PAYOUTS.STATUS.eq(PayoutStatus.PENDING.name()))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("Payout " + payout.payoutId().value() + " was not PENDING");
        }
    }
}
