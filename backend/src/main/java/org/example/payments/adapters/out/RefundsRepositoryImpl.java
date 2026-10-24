package org.example.payments.adapters.out;

import org.example.jooq.tables.records.RefundsRecord;
import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.domain.Refund;
import org.example.payments.core.domain.RefundAmounts;
import org.example.payments.core.domain.RefundId;
import org.example.payments.core.ports.out.DuplicateRefundExternalIdException;
import org.example.payments.core.ports.out.DuplicateRefundIdException;
import org.example.payments.core.ports.out.RefundsRepository;
import org.example.shared.persistence.UniqueViolations;
import org.example.shared.utils.PublicIds;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record5;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;

import static org.example.jooq.Tables.REFUNDS;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.sum;

@Repository
public class RefundsRepositoryImpl implements RefundsRepository {

    // Constraint names are set explicitly in V4__refunds.sql.
    static final String PUBLIC_ID_CONSTRAINT = "refunds_public_id_key";
    static final String MERCHANT_EXTERNAL_ID_CONSTRAINT = "refunds_merchant_id_external_id_key";

    private static final Field<BigDecimal> ZERO = inline(BigDecimal.ZERO);

    private final Logger logger = LoggerFactory.getLogger(RefundsRepositoryImpl.class);
    private final DSLContext dsl;

    public RefundsRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public RefundId nextId() {
        return new RefundId(PublicIds.generate("REF", 10));
    }

    @Override
    public Optional<Refund> findByMerchantAndExternalId(String merchantId, String externalId) {
        logger.info("Getting Refund by Merchant ID \"{}\" and External ID \"{}\"", merchantId, externalId);
        return dsl.selectFrom(REFUNDS)
                .where(REFUNDS.MERCHANT_ID.eq(merchantId))
                .and(REFUNDS.EXTERNAL_ID.eq(externalId))
                .fetchOptional()
                .map(RefundsRepositoryImpl::toDomain);
    }

    @Override
    public RefundAmounts refundedSoFar(PaymentId paymentId, Currency currency) {
        Record5<BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal> sums = dsl
                .select(coalesce(sum(REFUNDS.TOTAL_AMOUNT), ZERO),
                        coalesce(sum(REFUNDS.TAX_AMOUNT), ZERO),
                        coalesce(sum(REFUNDS.NET_AMOUNT), ZERO),
                        coalesce(sum(REFUNDS.FEE_AMOUNT), ZERO),
                        coalesce(sum(REFUNDS.MERCHANT_AMOUNT), ZERO))
                .from(REFUNDS)
                .where(REFUNDS.PAYMENT_ID.eq(paymentId.value()))
                .fetchSingle();
        int digits = currency.getDefaultFractionDigits();
        return new RefundAmounts(money(sums.value1(), digits), money(sums.value2(), digits),
                                 money(sums.value3(), digits), money(sums.value4(), digits),
                                 money(sums.value5(), digits));
    }

    @Override
    public Refund addRefund(Refund refund) {
        logger.info("Adding refund {} of payment {}", refund.refundId().value(), refund.paymentId().value());
        try {
            dsl.insertInto(REFUNDS)
                    .set(REFUNDS.PUBLIC_ID, refund.refundId().value())
                    .set(REFUNDS.PAYMENT_ID, refund.paymentId().value())
                    .set(REFUNDS.MERCHANT_ID, refund.merchantId())
                    .set(REFUNDS.EXTERNAL_ID, refund.externalId())
                    .set(REFUNDS.REFUNDED_AT, refund.refundedAt().atOffset(ZoneOffset.UTC))
                    .set(REFUNDS.FULL_REFUND, refund.fullRefund())
                    .set(REFUNDS.TAX_COUNTRY, refund.taxCountry())
                    .set(REFUNDS.CURRENCY, refund.currency().getCurrencyCode())
                    .set(REFUNDS.TAX_RATE, refund.taxRate())
                    .set(REFUNDS.FEE_RATE, refund.feeRate())
                    .set(REFUNDS.TOTAL_AMOUNT, refund.totalAmount())
                    .set(REFUNDS.TAX_AMOUNT, refund.taxAmount())
                    .set(REFUNDS.NET_AMOUNT, refund.netAmount())
                    .set(REFUNDS.FEE_AMOUNT, refund.feeAmount())
                    .set(REFUNDS.MERCHANT_AMOUNT, refund.merchantAmount())
                    .execute();
        } catch (DuplicateKeyException e) {
            String constraint = UniqueViolations.constraintName(e);
            if (PUBLIC_ID_CONSTRAINT.equals(constraint)) {
                throw new DuplicateRefundIdException(refund.refundId());
            }
            if (MERCHANT_EXTERNAL_ID_CONSTRAINT.equals(constraint)) {
                throw new DuplicateRefundExternalIdException(refund.merchantId(), refund.externalId());
            }
            throw e;
        }
        return refund;
    }

    private static Refund toDomain(RefundsRecord r) {
        Currency currency = Currency.getInstance(r.getCurrency());
        int digits = currency.getDefaultFractionDigits();
        return new Refund(
                new RefundId(r.getPublicId()),
                new PaymentId(r.getPaymentId()),
                r.getMerchantId(),
                r.getExternalId(),
                r.getRefundedAt().toInstant(),
                r.getFullRefund(),
                r.getTaxCountry(),
                currency,
                r.getTaxRate(),
                r.getFeeRate(),
                money(r.getTotalAmount(), digits),
                money(r.getTaxAmount(), digits),
                money(r.getNetAmount(), digits),
                money(r.getFeeAmount(), digits),
                money(r.getMerchantAmount(), digits));
    }

    /** numeric(19,4) comes back with 4 decimals; trim to the currency's (exact, since only zeros are dropped). */
    private static BigDecimal money(BigDecimal amount, int digits) {
        return amount.setScale(digits, RoundingMode.UNNECESSARY);
    }
}
