package org.example.payments;

import org.example.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.PaymentRequests.recordPayment;
import static org.example.PaymentRequests.recordRefund;
import static org.example.PaymentRequests.refund;
import static org.example.PaymentRequests.validPayment;
import static org.example.jooq.Tables.LEDGER_ACCOUNTS;
import static org.example.jooq.Tables.LEDGER_ENTRIES;
import static org.example.jooq.Tables.LEDGER_TRANSACTIONS;

/** The reference payment is 119.00 EUR at 19% tax and a 3% fee: tax 19.00, net 100.00, fee 3.00, merchant 97.00. */
class RefundsLedgerIT extends IntegrationTestBase {

    private static final String REFUNDED_AT = "2026-10-02T10:15:00+02:00";

    /** Entries of PAYMENT_REFUNDED transactions, keyed by "TYPE" or "TYPE/merchant-or-country". */
    private Map<String, BigDecimal> refundEntriesByAccount() {
        return dsl.select(LEDGER_ACCOUNTS.TYPE, LEDGER_ACCOUNTS.MERCHANT_ID, LEDGER_ACCOUNTS.COUNTRY_CODE,
                          LEDGER_ENTRIES.AMOUNT)
                .from(LEDGER_ENTRIES)
                .join(LEDGER_ACCOUNTS).on(LEDGER_ACCOUNTS.ID.eq(LEDGER_ENTRIES.ACCOUNT_ID))
                .join(LEDGER_TRANSACTIONS).on(LEDGER_TRANSACTIONS.ID.eq(LEDGER_ENTRIES.TRANSACTION_ID))
                .where(LEDGER_TRANSACTIONS.TYPE.eq("PAYMENT_REFUNDED"))
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.value1() + (r.value2() != null ? "/" + r.value2() : "")
                             + (r.value3() != null ? "/" + r.value3() : ""),
                        r -> r.value4(),
                        BigDecimal::add));
    }

    private BigDecimal merchantBalance(String merchantId, String currency) {
        return dsl.select(LEDGER_ACCOUNTS.BALANCE)
                .from(LEDGER_ACCOUNTS)
                .where(LEDGER_ACCOUNTS.TYPE.eq("MERCHANT_PAYABLE"))
                .and(LEDGER_ACCOUNTS.MERCHANT_ID.eq(merchantId))
                .and(LEDGER_ACCOUNTS.CURRENCY.eq(currency))
                .fetchSingle(LEDGER_ACCOUNTS.BALANCE);
    }

    @Test
    void newRefundPostsOneBalancedTransaction() {
        String paymentId = recordPayment(http, validPayment());

        Map<String, Object> refund = recordRefund(http, paymentId, refund("RE-1", REFUNDED_AT, "59.50"));

        var transaction = dsl.selectFrom(LEDGER_TRANSACTIONS)
                .where(LEDGER_TRANSACTIONS.TYPE.eq("PAYMENT_REFUNDED"))
                .fetchSingle();
        assertThat(transaction.getSourceType()).isEqualTo("refund");
        assertThat(transaction.getSourceId()).isEqualTo(refund.get("refundId"));
        assertThat(transaction.getEffectiveAt().toInstant().toString()).isEqualTo("2026-10-02T08:15:00Z");

        Map<String, BigDecimal> entries = refundEntriesByAccount();
        assertThat(entries).hasSize(4);
        assertThat(entries.get("CASH_CLEARING")).isEqualByComparingTo("-59.50");
        assertThat(entries.get("TAX_PAYABLE/DE")).isEqualByComparingTo("9.50");
        assertThat(entries.get("FEE_REVENUE")).isEqualByComparingTo("1.50");
        assertThat(entries.get("MERCHANT_PAYABLE/MERCH-001")).isEqualByComparingTo("48.50");
        // -97.00 owed after the payment, 48.50 of it refunded.
        assertThat(merchantBalance("MERCH-001", "EUR")).isEqualByComparingTo("-48.50");
    }

    @Test
    void fullyRefundedPaymentLeavesNothingOwed() {
        String paymentId = recordPayment(http, validPayment());

        recordRefund(http, paymentId, refund("RE-1", REFUNDED_AT, "59.50"));
        recordRefund(http, paymentId, refund("RE-2", REFUNDED_AT, null));

        assertThat(merchantBalance("MERCH-001", "EUR")).isEqualByComparingTo("0");
        Map<String, BigDecimal> entries = refundEntriesByAccount();
        assertThat(entries.get("TAX_PAYABLE/DE")).isEqualByComparingTo("19.00");
        assertThat(entries.get("FEE_REVENUE")).isEqualByComparingTo("3.00");
    }

    @Test
    void repeatDoesNotPostAgain() {
        String paymentId = recordPayment(http, validPayment());

        recordRefund(http, paymentId, refund("RE-1", REFUNDED_AT, "59.50"));
        recordRefund(http, paymentId, refund("RE-1", REFUNDED_AT, "59.50"));

        assertThat(dsl.fetchCount(LEDGER_TRANSACTIONS, LEDGER_TRANSACTIONS.TYPE.eq("PAYMENT_REFUNDED")))
                .isEqualTo(1);
        assertThat(merchantBalance("MERCH-001", "EUR")).isEqualByComparingTo("-48.50");
    }
}
