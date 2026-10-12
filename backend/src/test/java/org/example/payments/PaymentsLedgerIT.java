package org.example.payments;

import org.example.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.jooq.Tables.LEDGER_ACCOUNTS;
import static org.example.jooq.Tables.LEDGER_ENTRIES;
import static org.example.jooq.Tables.LEDGER_TRANSACTIONS;
import static org.example.payments.PaymentRequests.payment;
import static org.example.payments.PaymentRequests.post;
import static org.example.payments.PaymentRequests.validPayment;

class PaymentsLedgerIT extends IntegrationTestBase {

    /** Entries keyed by "TYPE" or "TYPE/merchant-or-country". */
    private Map<String, BigDecimal> entriesByAccount() {
        return dsl.select(LEDGER_ACCOUNTS.TYPE, LEDGER_ACCOUNTS.MERCHANT_ID, LEDGER_ACCOUNTS.COUNTRY_CODE,
                          LEDGER_ENTRIES.AMOUNT)
                .from(LEDGER_ENTRIES)
                .join(LEDGER_ACCOUNTS).on(LEDGER_ACCOUNTS.ID.eq(LEDGER_ENTRIES.ACCOUNT_ID))
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
    void newPaymentPostsOneBalancedTransaction() {
        Map<String, Object> body = post(http, validPayment()).getBody();

        var transaction = dsl.selectFrom(LEDGER_TRANSACTIONS).fetchSingle();
        assertThat(transaction.getType()).isEqualTo("PAYMENT_SETTLED");
        assertThat(transaction.getSourceType()).isEqualTo("payment");
        assertThat(transaction.getSourceId()).isEqualTo(body.get("paymentId"));
        assertThat(transaction.getEffectiveAt().toInstant().toString()).isEqualTo("2026-09-30T21:30:00Z");

        Map<String, BigDecimal> entries = entriesByAccount();
        assertThat(entries).hasSize(4);
        assertThat(entries.get("CASH_CLEARING")).isEqualByComparingTo("119.00");
        assertThat(entries.get("TAX_PAYABLE/DE")).isEqualByComparingTo("-19.00");
        assertThat(entries.get("FEE_REVENUE")).isEqualByComparingTo("-3.00");
        assertThat(entries.get("MERCHANT_PAYABLE/MERCH-001")).isEqualByComparingTo("-97.00");
        assertThat(merchantBalance("MERCH-001", "EUR")).isEqualByComparingTo("-97.00");
    }

    @Test
    void repeatDoesNotPostAgain() {
        post(http, validPayment());
        post(http, validPayment());

        assertThat(dsl.fetchCount(LEDGER_TRANSACTIONS)).isEqualTo(1);
        assertThat(merchantBalance("MERCH-001", "EUR")).isEqualByComparingTo("-97.00");
    }

    @Test
    void concurrentDifferentPaymentsForOneMerchantLoseNoUpdates() throws Exception {
        int requests = 10;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpStatus>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                // 10.00 EUR at 0% tax, 3% fee → merchant gets 9.70 each.
                Map<String, Object> body = payment("EXT-C" + i, "MERCH-001", "DE", "EUR", "10.00", "0",
                                                   "2026-09-30T21:30:00Z");
                futures.add(pool.submit(() -> {
                    start.await();
                    return HttpStatus.valueOf(post(http, body).getStatusCode().value());
                }));
            }
            start.countDown();
            for (Future<HttpStatus> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(HttpStatus.CREATED);
            }
        }

        assertThat(merchantBalance("MERCH-001", "EUR")).isEqualByComparingTo("-97.00");
        assertThat(entriesByAccount().get("MERCHANT_PAYABLE/MERCH-001")).isEqualByComparingTo("-97.00");
    }
}
