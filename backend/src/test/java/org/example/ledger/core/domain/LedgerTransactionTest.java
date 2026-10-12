package org.example.ledger.core.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTransactionTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant AT = Instant.parse("2026-09-30T21:30:00Z");

    private static LedgerEntry entry(AccountKey account, String amount) {
        return new LedgerEntry(account, new BigDecimal(amount));
    }

    private static LedgerTransaction transaction(List<LedgerEntry> entries) {
        return new LedgerTransaction("PAYMENT_SETTLED", "payment", "PAY-0000000001", AT, entries);
    }

    @Test
    void acceptsBalancedEntries() {
        LedgerTransaction transaction = transaction(List.of(
                entry(AccountKey.cashClearing(EUR), "119.00"),
                entry(AccountKey.taxPayable(EUR, "DE"), "-19.00"),
                entry(AccountKey.feeRevenue(EUR), "-3.00"),
                entry(AccountKey.merchantPayable(EUR, "MERCH-001"), "-97.00")));

        assertThat(transaction.entries()).hasSize(4);
        assertThat(LedgerTransaction.sum(transaction.entries())).isEqualByComparingTo("0");
    }

    @Test
    void acceptsZeroAmountEntries() {
        LedgerTransaction transaction = transaction(List.of(
                entry(AccountKey.cashClearing(EUR), "100.00"),
                entry(AccountKey.taxPayable(EUR, "US"), "0.00"),
                entry(AccountKey.merchantPayable(EUR, "MERCH-001"), "-100.00")));

        assertThat(transaction.entries()).hasSize(3);
    }

    @Test
    void rejectsUnbalancedEntries() {
        assertThatThrownBy(() -> transaction(List.of(
                entry(AccountKey.cashClearing(EUR), "119.00"),
                entry(AccountKey.merchantPayable(EUR, "MERCH-001"), "-118.99"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to zero");
    }

    @Test
    void rejectsMixedCurrenciesEvenWhenTheNumbersCancelOut() {
        assertThatThrownBy(() -> transaction(List.of(
                entry(AccountKey.cashClearing(EUR), "100.00"),
                entry(AccountKey.merchantPayable(USD, "MERCH-001"), "-100.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one currency");
    }

    @Test
    void rejectsFewerThanTwoEntries() {
        assertThatThrownBy(() -> transaction(List.of(entry(AccountKey.cashClearing(EUR), "0.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two entries");
    }

    @Test
    void copiesEntriesDefensively() {
        List<LedgerEntry> entries = new ArrayList<>(List.of(
                entry(AccountKey.cashClearing(EUR), "1.00"),
                entry(AccountKey.merchantPayable(EUR, "MERCH-001"), "-1.00")));
        LedgerTransaction transaction = transaction(entries);

        entries.clear();

        assertThat(transaction.entries()).hasSize(2);
    }
}
