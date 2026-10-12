package org.example.payments.core.ports.in;

import org.example.payments.core.domain.Customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;

public record RecordSettledPaymentCommand(
        String externalId,
        Instant settledAt,
        String merchantId,
        Customer customer,
        Currency currency,
        BigDecimal totalAmount,
        BigDecimal taxRate) {

    /** payments.total_amount is numeric(19,4): at most 15 integer digits. */
    private static final BigDecimal MAX_TOTAL_AMOUNT = new BigDecimal("1000000000000000");
    /** payments.tax_rate is numeric(9,6). */
    private static final int MAX_RATE_DECIMALS = 6;
    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    public RecordSettledPaymentCommand {
        requireNotBlank(externalId, "externalId");
        requireNotBlank(merchantId, "merchantId");
        // Postgres timestamptz stores microseconds; truncating here makes a resent value equal the stored one.
        settledAt = settledAt.truncatedTo(ChronoUnit.MICROS);

        int digits = currency.getDefaultFractionDigits();
        if (digits < 0) {
            throw new InvalidPaymentException("Currency " + currency + " has no minor units and is not supported");
        }
        if (totalAmount.signum() < 0) {
            throw new InvalidPaymentException("totalAmount cannot be negative: " + totalAmount);
        }
        if (totalAmount.compareTo(MAX_TOTAL_AMOUNT) >= 0) {
            throw new InvalidPaymentException("totalAmount is too large (must be below 10^15): " + totalAmount);
        }
        if (totalAmount.stripTrailingZeros().scale() > digits) {
            throw new InvalidPaymentException("totalAmount has more decimal places than " + currency
                                              + " allows (" + digits + "): " + totalAmount);
        }
        if (taxRate.signum() < 0) {
            throw new InvalidPaymentException("taxRate cannot be negative: " + taxRate);
        }
        if (taxRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new InvalidPaymentException("taxRate must be a fraction below 1 (e.g. 0.19 for 19%): " + taxRate);
        }
        if (taxRate.stripTrailingZeros().scale() > MAX_RATE_DECIMALS) {
            throw new InvalidPaymentException("taxRate can have at most " + MAX_RATE_DECIMALS
                                              + " decimal places: " + taxRate);
        }
        String countryCode = customer.billingContact().countryCode();
        if (!ISO_COUNTRIES.contains(countryCode)) {
            throw new InvalidPaymentException(
                    "billingContact.countryCode must be an ISO 3166-1 alpha-2 code, got: " + countryCode);
        }
    }

    private static void requireNotBlank(String value, String field) {
        if (value.isBlank()) {
            throw new InvalidPaymentException(field + " must not be blank");
        }
    }
}
