package org.example.ledger.adapters.out;

import org.example.IntegrationTestBase;
import org.example.ledger.core.domain.AccountKey;
import org.example.ledger.core.domain.LedgerEntry;
import org.example.ledger.core.domain.LedgerTransaction;
import org.example.ledger.core.domain.MerchantPayable;
import org.example.ledger.core.domain.TaxPayable;
import org.example.ledger.core.ports.in.PostToLedgerUseCase;
import org.example.ledger.core.ports.out.LedgerQueries;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerQueriesIT extends IntegrationTestBase {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Instant JULY = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant OCTOBER = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private PostToLedgerUseCase postToLedger;

    @Autowired
    private LedgerQueries ledgerQueries;

    /** A settlement without a fee: cash in, tax and merchant share owed out. Amounts are positive here. */
    private void settle(String sourceId, String merchantId, String countryCode, Currency currency,
                        String effectiveAt, String tax, String merchant) {
        BigDecimal taxAmount = new BigDecimal(tax);
        BigDecimal merchantAmount = new BigDecimal(merchant);
        postToLedger.post(new LedgerTransaction("PAYMENT_SETTLED", "payment", sourceId,
                                                Instant.parse(effectiveAt), List.of(
                new LedgerEntry(AccountKey.cashClearing(currency), taxAmount.add(merchantAmount)),
                new LedgerEntry(AccountKey.taxPayable(currency, countryCode), taxAmount.negate()),
                new LedgerEntry(AccountKey.merchantPayable(currency, merchantId), merchantAmount.negate()))));
    }

    private void hold(String sourceId, String merchantId, Currency currency, String amount) {
        BigDecimal held = new BigDecimal(amount);
        postToLedger.post(new LedgerTransaction("PAYOUT_HELD", "payout", sourceId,
                                                Instant.parse("2026-09-01T10:00:00Z"), List.of(
                new LedgerEntry(AccountKey.merchantPayable(currency, merchantId), held),
                new LedgerEntry(AccountKey.merchantPayoutPending(currency, merchantId), held.negate()))));
    }

    @Test
    void merchantPayablesArePositiveAtCurrencyScaleOrderedByCurrency() {
        settle("P-1", "MERCH-001", "DE", EUR, "2026-07-15T10:00:00Z", "19.00", "97.00");
        settle("P-2", "MERCH-001", "DE", EUR, "2026-07-16T10:00:00Z", "19.00", "97.00");
        settle("P-3", "MERCH-001", "US", USD, "2026-08-20T10:00:00Z", "8.00", "97.00");
        settle("P-4", "MERCH-001", "JP", JPY, "2026-08-21T10:00:00Z", "150", "1455");
        settle("P-5", "MERCH-002", "DE", EUR, "2026-08-22T10:00:00Z", "38.00", "190.00");

        // Record equality compares BigDecimal with equals, so this also pins the scale.
        assertThat(ledgerQueries.merchantPayables("MERCH-001")).containsExactly(
                new MerchantPayable("MERCH-001", EUR, new BigDecimal("194.00"), new BigDecimal("0.00")),
                new MerchantPayable("MERCH-001", JPY, new BigDecimal("1455"), new BigDecimal("0")),
                new MerchantPayable("MERCH-001", USD, new BigDecimal("97.00"), new BigDecimal("0.00")));
    }

    @Test
    void merchantPayablesSplitWhatIsHeldForPayoutsFromWhatIsAvailable() {
        settle("P-1", "MERCH-001", "DE", EUR, "2026-07-15T10:00:00Z", "19.00", "97.00");
        settle("P-2", "MERCH-001", "US", USD, "2026-08-20T10:00:00Z", "8.00", "97.00");
        // Two holds in EUR, as payouts do it: available to pending.
        hold("PO-1", "MERCH-001", EUR, "40.00");
        hold("PO-2", "MERCH-001", EUR, "10.50");

        assertThat(ledgerQueries.merchantPayables("MERCH-001")).containsExactly(
                new MerchantPayable("MERCH-001", EUR, new BigDecimal("46.50"), new BigDecimal("50.50")),
                new MerchantPayable("MERCH-001", USD, new BigDecimal("97.00"), new BigDecimal("0.00")));
        assertThat(ledgerQueries.merchantPayables("MERCH-001").getFirst().payable())
                .isEqualByComparingTo("97.00");
    }

    @Test
    void merchantPayablesMatchTheMerchantIdExactly() {
        settle("P-1", "MERCH-001", "DE", EUR, "2026-07-15T10:00:00Z", "19.00", "97.00");

        assertThat(ledgerQueries.merchantPayables("merch-001")).isEmpty();
        assertThat(ledgerQueries.merchantPayables("MERCH-002")).isEmpty();
    }

    @Test
    void taxPayablesSumPerCountryAndCurrencyInsideTheHalfOpenPeriod() {
        settle("P-1", "MERCH-001", "JP", JPY, "2026-06-30T23:59:59.999999Z", "150", "1455");   // before: out
        settle("P-2", "MERCH-001", "DE", EUR, "2026-07-01T00:00:00Z", "19.00", "97.00");        // on from: in
        settle("P-3", "MERCH-001", "GR", EUR, "2026-08-01T10:00:00Z", "24.00", "97.00");
        settle("P-4", "MERCH-001", "US", USD, "2026-08-20T10:00:00Z", "8.00", "97.00");
        settle("P-5", "MERCH-002", "DE", EUR, "2026-09-30T23:59:59.999999Z", "38.00", "190.00"); // last instant: in
        settle("P-6", "MERCH-002", "DE", EUR, "2026-10-01T00:00:00Z", "19.00", "97.00");        // on to: out

        assertThat(ledgerQueries.taxPayables(JULY, OCTOBER)).containsExactly(
                new TaxPayable("DE", EUR, new BigDecimal("57.00")),
                new TaxPayable("GR", EUR, new BigDecimal("24.00")),
                new TaxPayable("US", USD, new BigDecimal("8.00")));
    }

    @Test
    void zeroTaxCountryIsListedWithZero() {
        settle("P-1", "MERCH-001", "HK", EUR, "2026-07-15T10:00:00Z", "0.00", "100.00");

        assertThat(ledgerQueries.taxPayables(JULY, OCTOBER))
                .containsExactly(new TaxPayable("HK", EUR, new BigDecimal("0.00")));
    }

    @Test
    void periodWithoutPaymentsIsEmpty() {
        settle("P-1", "MERCH-001", "DE", EUR, "2026-07-15T10:00:00Z", "19.00", "97.00");

        assertThat(ledgerQueries.taxPayables(OCTOBER, Instant.parse("2027-01-01T00:00:00Z"))).isEmpty();
    }
}
