package org.example.payments.adapters.out;

import org.example.ledger.core.domain.AccountKey;
import org.example.ledger.core.domain.LedgerEntry;
import org.example.ledger.core.domain.LedgerTransaction;
import org.example.payments.core.domain.Contact;
import org.example.payments.core.domain.Customer;
import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.PaymentId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerSettlementAdapterTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    private static Payment payment(String taxCountry, String total, String taxRate, String tax, String net,
                                   String fee, String merchant) {
        return new Payment(new PaymentId("PAY-0000000001"), "EXT-1", Instant.parse("2026-09-30T21:30:00Z"),
                           "MERCH-001",
                           new Customer("Anna", "Schmidt", "anna@example.com",
                                        new Contact("Hauptstrasse 12", null, taxCountry, "10115")),
                           taxCountry, EUR, new BigDecimal(total), new BigDecimal(taxRate), new BigDecimal(tax),
                           new BigDecimal(net), new BigDecimal("0.03"), new BigDecimal(fee),
                           new BigDecimal(merchant));
    }

    @Test
    void mapsSettledPaymentToFourBalancedLines() {
        Payment payment = payment("DE", "119.00", "0.19", "19.00", "100.00", "3.00", "97.00");

        LedgerTransaction transaction = LedgerSettlementAdapter.toLedgerTransaction(payment);

        assertThat(transaction.type()).isEqualTo("PAYMENT_SETTLED");
        assertThat(transaction.sourceType()).isEqualTo("payment");
        assertThat(transaction.sourceId()).isEqualTo("PAY-0000000001");
        assertThat(transaction.effectiveAt()).isEqualTo(payment.settledAt());
        assertThat(transaction.entries()).containsExactly(
                new LedgerEntry(AccountKey.cashClearing(EUR), new BigDecimal("119.00")),
                new LedgerEntry(AccountKey.taxPayable(EUR, "DE"), new BigDecimal("-19.00")),
                new LedgerEntry(AccountKey.feeRevenue(EUR), new BigDecimal("-3.00")),
                new LedgerEntry(AccountKey.merchantPayable(EUR, "MERCH-001"), new BigDecimal("-97.00")));
    }

    @Test
    void zeroTaxStillPostsATaxLine() {
        LedgerTransaction transaction = LedgerSettlementAdapter.toLedgerTransaction(
                payment("US", "100.00", "0", "0.00", "100.00", "3.00", "97.00"));

        assertThat(transaction.entries()).hasSize(4)
                .contains(new LedgerEntry(AccountKey.taxPayable(EUR, "US"), new BigDecimal("-0.00")));
    }
}
