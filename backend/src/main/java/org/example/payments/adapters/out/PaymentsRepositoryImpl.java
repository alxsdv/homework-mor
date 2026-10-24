package org.example.payments.adapters.out;

import org.example.jooq.tables.records.PaymentsRecord;
import org.example.payments.core.domain.Contact;
import org.example.payments.core.domain.Customer;
import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.ports.out.DuplicateExternalIdException;
import org.example.payments.core.ports.out.DuplicatePaymentIdException;
import org.example.payments.core.ports.out.PaymentsRepository;
import org.example.shared.persistence.UniqueViolations;
import org.example.shared.utils.PublicIds;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;

import static org.example.jooq.Tables.PAYMENTS;

@Repository
public class PaymentsRepositoryImpl implements PaymentsRepository {

    // Constraint names are set explicitly in V1__payments.sql.
    static final String PUBLIC_ID_CONSTRAINT = "payments_public_id_key";
    static final String MERCHANT_EXTERNAL_ID_CONSTRAINT = "payments_merchant_id_external_id_key";

    private final Logger logger = LoggerFactory.getLogger(PaymentsRepositoryImpl.class);
    private final DSLContext dsl;

    public PaymentsRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public PaymentId nextId() {
        return new PaymentId(PublicIds.generate("PAY", 10));
    }

    @Override
    public Optional<Payment> findByMerchantAndExternalId(String merchantId, String externalId) {
        logger.info("Getting Payment by Merchant ID \"{}\" and External ID \"{}\"", merchantId, externalId);
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.MERCHANT_ID.eq(merchantId))
                .and(PAYMENTS.EXTERNAL_ID.eq(externalId))
                .fetchOptional()
                .map(PaymentsRepositoryImpl::toDomain);
    }

    @Override
    public Optional<Payment> findByIdForUpdate(PaymentId paymentId) {
        logger.info("Locking payment {}", paymentId.value());
        return dsl.selectFrom(PAYMENTS)
                .where(PAYMENTS.PUBLIC_ID.eq(paymentId.value()))
                .forUpdate()
                .fetchOptional()
                .map(PaymentsRepositoryImpl::toDomain);
    }

    @Override
    public Payment addPayment(Payment payment) {
        logger.info("Adding new payment {} for merchant ID {}", payment.paymentId().value(), payment.merchantId());
        Customer customer = payment.customer();
        Contact contact = customer.billingContact();
        try {
            dsl.insertInto(PAYMENTS)
                    .set(PAYMENTS.PUBLIC_ID, payment.paymentId().value())
                    .set(PAYMENTS.MERCHANT_ID, payment.merchantId())
                    .set(PAYMENTS.EXTERNAL_ID, payment.externalId())
                    .set(PAYMENTS.SETTLED_AT, payment.settledAt().atOffset(ZoneOffset.UTC))
                    .set(PAYMENTS.CUSTOMER_FIRST_NAME, customer.firstName())
                    .set(PAYMENTS.CUSTOMER_LAST_NAME, customer.lastName())
                    .set(PAYMENTS.CUSTOMER_EMAIL, customer.email())
                    .set(PAYMENTS.BILLING_ADDRESS_LINE1, contact.addressLine1())
                    .set(PAYMENTS.BILLING_ADDRESS_LINE2, contact.addressLine2())
                    .set(PAYMENTS.BILLING_COUNTRY_CODE, contact.countryCode())
                    .set(PAYMENTS.BILLING_POSTAL_CODE, contact.postalCode())
                    .set(PAYMENTS.TAX_COUNTRY, payment.taxCountry())
                    .set(PAYMENTS.CURRENCY, payment.currency().getCurrencyCode())
                    .set(PAYMENTS.TOTAL_AMOUNT, payment.totalAmount())
                    .set(PAYMENTS.TAX_RATE, payment.taxRate())
                    .set(PAYMENTS.TAX_AMOUNT, payment.taxAmount())
                    .set(PAYMENTS.NET_AMOUNT, payment.netAmount())
                    .set(PAYMENTS.FEE_RATE, payment.feeRate())
                    .set(PAYMENTS.FEE_AMOUNT, payment.feeAmount())
                    .set(PAYMENTS.MERCHANT_AMOUNT, payment.merchantAmount())
                    .execute();
        } catch (DuplicateKeyException e) {
            String constraint = UniqueViolations.constraintName(e);
            if (PUBLIC_ID_CONSTRAINT.equals(constraint)) {
                throw new DuplicatePaymentIdException(payment.paymentId());
            }
            if (MERCHANT_EXTERNAL_ID_CONSTRAINT.equals(constraint)) {
                throw new DuplicateExternalIdException(payment.merchantId(), payment.externalId());
            }
            throw e;
        }
        return payment;
    }

    private static Payment toDomain(PaymentsRecord r) {
        Currency currency = Currency.getInstance(r.getCurrency());
        int digits = currency.getDefaultFractionDigits();
        return new Payment(
                new PaymentId(r.getPublicId()),
                r.getExternalId(),
                r.getSettledAt().toInstant(),
                r.getMerchantId(),
                new Customer(r.getCustomerFirstName(), r.getCustomerLastName(), r.getCustomerEmail(),
                             new Contact(r.getBillingAddressLine1(), r.getBillingAddressLine2(),
                                         r.getBillingCountryCode(), r.getBillingPostalCode())),
                r.getTaxCountry(),
                currency,
                money(r.getTotalAmount(), digits),
                r.getTaxRate(),
                money(r.getTaxAmount(), digits),
                money(r.getNetAmount(), digits),
                r.getFeeRate(),
                money(r.getFeeAmount(), digits),
                money(r.getMerchantAmount(), digits));
    }

    /** numeric(19,4) comes back with 4 decimals; trim to the currency's (exact, since only zeros are dropped). */
    private static BigDecimal money(BigDecimal amount, int digits) {
        return amount.setScale(digits, RoundingMode.UNNECESSARY);
    }
}
