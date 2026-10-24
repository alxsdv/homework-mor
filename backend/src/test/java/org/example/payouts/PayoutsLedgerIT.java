package org.example.payouts;

import org.example.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.PaymentRequests.recordPayment;
import static org.example.PaymentRequests.recordRefund;
import static org.example.PaymentRequests.refund;
import static org.example.PaymentRequests.validPayment;
import static org.example.PayoutRequests.eurPayout;
import static org.example.PayoutRequests.fail;
import static org.example.PayoutRequests.requestPayout;
import static org.example.PayoutRequests.settle;
import static org.example.jooq.Tables.LEDGER_ACCOUNTS;
import static org.example.jooq.Tables.LEDGER_ENTRIES;
import static org.example.jooq.Tables.LEDGER_TRANSACTIONS;
import static org.jooq.impl.DSL.sum;

/**
 * The design document's example: MERCH-001 is owed 48.50 EUR after the 119.00 payment and its 59.50 refund, and
 * gets it all paid out.
 */
class PayoutsLedgerIT extends IntegrationTestBase {

    private static final String SETTLED_AT = "2026-10-05T10:00:00+02:00";

    private String payoutId;

    @BeforeEach
    void recordTheExampleAndRequestThePayout() {
        String paymentId = recordPayment(http, validPayment());
        recordRefund(http, paymentId, refund("RE-1", "2026-10-02T10:15:00+02:00", "59.50"));
        payoutId = requestPayout(http, eurPayout("PO-EXT-1", "48.50"));
    }

    /** Entries of one ledger transaction type, keyed by "TYPE" or "TYPE/merchant". */
    private Map<String, BigDecimal> entriesOf(String transactionType) {
        return dsl.select(LEDGER_ACCOUNTS.TYPE, LEDGER_ACCOUNTS.MERCHANT_ID, LEDGER_ENTRIES.AMOUNT)
                .from(LEDGER_ENTRIES)
                .join(LEDGER_ACCOUNTS).on(LEDGER_ACCOUNTS.ID.eq(LEDGER_ENTRIES.ACCOUNT_ID))
                .join(LEDGER_TRANSACTIONS).on(LEDGER_TRANSACTIONS.ID.eq(LEDGER_ENTRIES.TRANSACTION_ID))
                .where(LEDGER_TRANSACTIONS.TYPE.eq(transactionType))
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.value1() + (r.value2() != null ? "/" + r.value2() : ""),
                        r -> r.value3(),
                        BigDecimal::add));
    }

    /** Sum of all entries on the account, positive when it's a debit balance. */
    private BigDecimal balanceOf(String accountType) {
        BigDecimal balance = dsl.select(sum(LEDGER_ENTRIES.AMOUNT))
                .from(LEDGER_ENTRIES)
                .join(LEDGER_ACCOUNTS).on(LEDGER_ACCOUNTS.ID.eq(LEDGER_ENTRIES.ACCOUNT_ID))
                .where(LEDGER_ACCOUNTS.TYPE.eq(accountType))
                .fetchSingle()
                .value1();
        return balance == null ? BigDecimal.ZERO : balance;
    }

    private BigDecimal cachedMerchantBalance() {
        return dsl.select(LEDGER_ACCOUNTS.BALANCE)
                .from(LEDGER_ACCOUNTS)
                .where(LEDGER_ACCOUNTS.TYPE.eq("MERCHANT_PAYABLE"))
                .and(LEDGER_ACCOUNTS.MERCHANT_ID.eq("MERCH-001"))
                .fetchSingle(LEDGER_ACCOUNTS.BALANCE);
    }

    @Test
    void holdMovesTheAmountFromAvailableToPendingAndLeavesCash() {
        var transaction = dsl.selectFrom(LEDGER_TRANSACTIONS)
                .where(LEDGER_TRANSACTIONS.TYPE.eq("PAYOUT_HELD"))
                .fetchSingle();
        assertThat(transaction.getSourceType()).isEqualTo("payout");
        assertThat(transaction.getSourceId()).isEqualTo(payoutId);
        assertThat(transaction.getEffectiveAt().toInstant().toString()).isEqualTo("2026-10-03T07:00:00Z");

        Map<String, BigDecimal> entries = entriesOf("PAYOUT_HELD");
        assertThat(entries).hasSize(2);
        assertThat(entries.get("MERCHANT_PAYABLE/MERCH-001")).isEqualByComparingTo("48.50");
        assertThat(entries.get("MERCHANT_PAYOUT_PENDING/MERCH-001")).isEqualByComparingTo("-48.50");

        assertThat(cachedMerchantBalance()).isEqualByComparingTo("0");
        assertThat(balanceOf("MERCHANT_PAYOUT_PENDING")).isEqualByComparingTo("-48.50");
        assertThat(balanceOf("CASH_CLEARING")).isEqualByComparingTo("59.50");
    }

    @Test
    void settlementTakesThePendingAmountOutOfCash() {
        settle(http, payoutId, SETTLED_AT);

        var transaction = dsl.selectFrom(LEDGER_TRANSACTIONS)
                .where(LEDGER_TRANSACTIONS.TYPE.eq("PAYOUT_SETTLED"))
                .fetchSingle();
        assertThat(transaction.getSourceId()).isEqualTo(payoutId);
        assertThat(transaction.getEffectiveAt().toInstant().toString()).isEqualTo("2026-10-05T08:00:00Z");

        Map<String, BigDecimal> entries = entriesOf("PAYOUT_SETTLED");
        assertThat(entries).hasSize(2);
        assertThat(entries.get("MERCHANT_PAYOUT_PENDING/MERCH-001")).isEqualByComparingTo("48.50");
        assertThat(entries.get("CASH_CLEARING")).isEqualByComparingTo("-48.50");

        // What's left in cash is exactly what we owe the tax authority (9.50) plus the fees we earned (1.50).
        assertThat(balanceOf("MERCHANT_PAYOUT_PENDING")).isEqualByComparingTo("0");
        assertThat(balanceOf("CASH_CLEARING")).isEqualByComparingTo("11.00");
        assertThat(balanceOf("TAX_PAYABLE")).isEqualByComparingTo("-9.50");
        assertThat(balanceOf("FEE_REVENUE")).isEqualByComparingTo("-1.50");
        assertThat(cachedMerchantBalance()).isEqualByComparingTo("0");
    }

    @Test
    void failureReleasesThePendingAmountBackToAvailable() {
        fail(http, payoutId, SETTLED_AT);

        Map<String, BigDecimal> entries = entriesOf("PAYOUT_RELEASED");
        assertThat(entries).hasSize(2);
        assertThat(entries.get("MERCHANT_PAYOUT_PENDING/MERCH-001")).isEqualByComparingTo("48.50");
        assertThat(entries.get("MERCHANT_PAYABLE/MERCH-001")).isEqualByComparingTo("-48.50");

        assertThat(cachedMerchantBalance()).isEqualByComparingTo("-48.50");
        assertThat(balanceOf("MERCHANT_PAYOUT_PENDING")).isEqualByComparingTo("0");
        assertThat(balanceOf("CASH_CLEARING")).isEqualByComparingTo("59.50");
    }
}
