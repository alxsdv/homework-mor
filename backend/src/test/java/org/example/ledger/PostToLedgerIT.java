package org.example.ledger;

import org.example.IntegrationTestBase;
import org.example.ledger.core.domain.AccountKey;
import org.example.ledger.core.domain.LedgerEntry;
import org.example.ledger.core.domain.LedgerTransaction;
import org.example.ledger.core.ports.in.PostToLedgerUseCase;
import org.example.shared.transactions.TransactionRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.jooq.Tables.LEDGER_ACCOUNTS;
import static org.example.jooq.Tables.LEDGER_ENTRIES;
import static org.example.jooq.Tables.LEDGER_TRANSACTIONS;
import static org.jooq.impl.DSL.sum;

class PostToLedgerIT extends IntegrationTestBase {

    private static final Currency EUR = Currency.getInstance("EUR");

    @Autowired
    private PostToLedgerUseCase postToLedger;

    @Autowired
    private TransactionRunner transactionRunner;

    /** A settled-payment-shaped ledger transaction for a German sale. Amounts are positive; credits are negated. */
    private static LedgerTransaction settlement(String sourceId, String merchantId, String total, String tax,
                                                String fee, String merchant) {
        Instant effectiveAt = Instant.parse("2026-09-30T21:30:00Z");
        return new LedgerTransaction("PAYMENT_SETTLED", "payment", sourceId, effectiveAt, List.of(
                new LedgerEntry(AccountKey.cashClearing(EUR), new BigDecimal(total)),
                new LedgerEntry(AccountKey.taxPayable(EUR, "DE"), new BigDecimal(tax).negate()),
                new LedgerEntry(AccountKey.feeRevenue(EUR), new BigDecimal(fee).negate()),
                new LedgerEntry(AccountKey.merchantPayable(EUR, merchantId), new BigDecimal(merchant).negate())));
    }

    private BigDecimal merchantBalance(String merchantId) {
        return dsl.select(LEDGER_ACCOUNTS.BALANCE)
                .from(LEDGER_ACCOUNTS)
                .where(LEDGER_ACCOUNTS.TYPE.eq("MERCHANT_PAYABLE"))
                .and(LEDGER_ACCOUNTS.MERCHANT_ID.eq(merchantId))
                .fetchSingle(LEDGER_ACCOUNTS.BALANCE);
    }

    private BigDecimal merchantEntriesSum(String merchantId) {
        return dsl.select(sum(LEDGER_ENTRIES.AMOUNT))
                .from(LEDGER_ENTRIES)
                .join(LEDGER_ACCOUNTS).on(LEDGER_ACCOUNTS.ID.eq(LEDGER_ENTRIES.ACCOUNT_ID))
                .where(LEDGER_ACCOUNTS.TYPE.eq("MERCHANT_PAYABLE"))
                .and(LEDGER_ACCOUNTS.MERCHANT_ID.eq(merchantId))
                .fetchSingle()
                .value1();
    }

    @Test
    void postingCreatesAccountsTransactionEntriesAndMerchantBalance() {
        postToLedger.post(settlement("PAY-0000000001", "MERCH-001", "119.00", "19.00", "3.00", "97.00"));

        assertThat(dsl.fetchCount(LEDGER_TRANSACTIONS)).isEqualTo(1);
        assertThat(dsl.fetchCount(LEDGER_ENTRIES)).isEqualTo(4);
        assertThat(dsl.fetchCount(LEDGER_ACCOUNTS)).isEqualTo(4);
        assertThat(merchantBalance("MERCH-001")).isEqualByComparingTo("-97.00");
        // System accounts keep no cached balance.
        assertThat(dsl.fetchCount(LEDGER_ACCOUNTS, LEDGER_ACCOUNTS.BALANCE.isNull())).isEqualTo(3);
        var transaction = dsl.selectFrom(LEDGER_TRANSACTIONS).fetchSingle();
        assertThat(transaction.getType()).isEqualTo("PAYMENT_SETTLED");
        assertThat(transaction.getSourceType()).isEqualTo("payment");
        assertThat(transaction.getSourceId()).isEqualTo("PAY-0000000001");
        assertThat(transaction.getEffectiveAt().toInstant()).isEqualTo(Instant.parse("2026-09-30T21:30:00Z"));
    }

    @Test
    void secondPostingReusesAccountsAndMovesBalance() {
        postToLedger.post(settlement("PAY-0000000001", "MERCH-001", "119.00", "19.00", "3.00", "97.00"));
        postToLedger.post(settlement("PAY-0000000002", "MERCH-001", "119.00", "19.00", "3.00", "97.00"));

        assertThat(dsl.fetchCount(LEDGER_ACCOUNTS)).isEqualTo(4);
        assertThat(merchantBalance("MERCH-001")).isEqualByComparingTo("-194.00");
        assertThat(merchantEntriesSum("MERCH-001")).isEqualByComparingTo("-194.00");
    }

    @Test
    void duplicateSourceIsRejectedAndLeavesNothingBehind() {
        postToLedger.post(settlement("PAY-0000000001", "MERCH-001", "119.00", "19.00", "3.00", "97.00"));

        assertThatThrownBy(() -> postToLedger.post(
                settlement("PAY-0000000001", "MERCH-001", "119.00", "19.00", "3.00", "97.00")))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(dsl.fetchCount(LEDGER_TRANSACTIONS)).isEqualTo(1);
        assertThat(dsl.fetchCount(LEDGER_ENTRIES)).isEqualTo(4);
        assertThat(merchantBalance("MERCH-001")).isEqualByComparingTo("-97.00");
    }

    @Test
    void postingJoinsTheCallersTransaction() {
        // A lambda that only throws fits both inTransaction overloads; a typed Runnable picks one.
        Runnable postThenFail = () -> {
            postToLedger.post(settlement("PAY-0000000001", "MERCH-001", "119.00", "19.00", "3.00", "97.00"));
            throw new IllegalStateException("outer failure after posting");
        };

        assertThatThrownBy(() -> transactionRunner.inTransaction(postThenFail))
                .isInstanceOf(IllegalStateException.class);

        assertThat(dsl.fetchCount(LEDGER_TRANSACTIONS)).isZero();
        assertThat(dsl.fetchCount(LEDGER_ENTRIES)).isZero();
        assertThat(dsl.fetchCount(LEDGER_ACCOUNTS)).isZero();
    }

    @Test
    void concurrentPostingsForOneMerchantLoseNoUpdates() throws Exception {
        int postings = 10;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(postings)) {
            for (int i = 0; i < postings; i++) {
                String sourceId = "PAY-C" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    postToLedger.post(settlement(sourceId, "MERCH-001", "1.00", "0.00", "0.00", "1.00"));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(merchantBalance("MERCH-001")).isEqualByComparingTo("-10.00");
        assertThat(merchantEntriesSum("MERCH-001")).isEqualByComparingTo("-10.00");
    }

    @Test
    void databaseRejectsAccountRowsWithWrongKeyParts() {
        // Second line of defence behind AccountKey's constructor (V3 CHECK constraint).
        assertThatThrownBy(() -> dsl.execute(
                "insert into ledger_accounts (type, currency, merchant_id, country_code, balance) "
                + "values ('MERCHANT_PAYABLE', 'EUR', null, null, 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
