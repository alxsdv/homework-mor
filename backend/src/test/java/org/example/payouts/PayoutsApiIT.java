package org.example.payouts;

import org.example.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.PaymentRequests.recordPayment;
import static org.example.PaymentRequests.recordRefund;
import static org.example.PaymentRequests.refund;
import static org.example.PaymentRequests.validPayment;
import static org.example.PayoutRequests.eurPayout;
import static org.example.PayoutRequests.fail;
import static org.example.PayoutRequests.getBalances;
import static org.example.PayoutRequests.payout;
import static org.example.PayoutRequests.postPayout;
import static org.example.PayoutRequests.requestPayout;
import static org.example.PayoutRequests.settle;
import static org.example.PayoutRequests.with;
import static org.example.jooq.Tables.LEDGER_TRANSACTIONS;
import static org.example.jooq.Tables.PAYOUTS;

/** Every test starts with the reference payment: 97.00 EUR available to MERCH-001. */
class PayoutsApiIT extends IntegrationTestBase {

    private static final String SETTLED_AT = "2026-10-05T10:00:00+02:00";

    private String paymentId;

    @BeforeEach
    void recordReferencePayment() {
        paymentId = recordPayment(http, validPayment());
    }

    private Map<String, Object> eurBalance() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> balances =
                (List<Map<String, Object>>) getBalances(http, "MERCH-001").getBody().get("balances");
        assertThat(balances).hasSize(1);
        return balances.getFirst();
    }

    private static Map<String, Object> balance(String payable, String available, String pendingPayout) {
        return Map.of("currency", "EUR", "payable", payable, "available", available, "pendingPayout", pendingPayout);
    }

    @Test
    void requestedPayoutIsPendingAndHoldsItsAmount() {
        ResponseEntity<Map<String, Object>> response = postPayout(http, eurPayout("PO-EXT-1", "60.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody())
                .containsEntry("externalId", "PO-EXT-1")
                .containsEntry("merchantId", "MERCH-001")
                .containsEntry("currency", "EUR")
                .containsEntry("amount", "60.00")
                .containsEntry("status", "PENDING")
                .containsEntry("requestedAt", "2026-10-03T07:00:00Z")
                .containsEntry("completedAt", null);
        assertThat((String) response.getBody().get("payoutId")).matches("PO-[0-9A-Z]{10}");
        // We still owe all 97.00, but only 37.00 can be paid out again.
        assertThat(eurBalance()).isEqualTo(balance("97.00", "37.00", "60.00"));
    }

    @Test
    void settledPayoutIsNoLongerOwed() {
        String payoutId = requestPayout(http, eurPayout("PO-EXT-1", "60.00"));

        ResponseEntity<Map<String, Object>> response = settle(http, payoutId, SETTLED_AT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("payoutId", payoutId)
                .containsEntry("status", "SETTLED")
                .containsEntry("completedAt", "2026-10-05T08:00:00Z");
        assertThat(eurBalance()).isEqualTo(balance("37.00", "37.00", "0.00"));
    }

    @Test
    void failedPayoutReleasesItsHold() {
        String payoutId = requestPayout(http, eurPayout("PO-EXT-1", "60.00"));

        ResponseEntity<Map<String, Object>> response = fail(http, payoutId, SETTLED_AT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "FAILED");
        assertThat(eurBalance()).isEqualTo(balance("97.00", "97.00", "0.00"));
        // Released money can be paid out again.
        assertThat(postPayout(http, eurPayout("PO-EXT-2", "97.00")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void payoutBeyondTheAvailableBalanceIsRejectedAndLeavesNothingBehind() {
        ResponseEntity<Map<String, Object>> response = postPayout(http, eurPayout("PO-EXT-1", "97.01"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) response.getBody().get("detail"))
                .isEqualTo("Payout of 97.01 EUR exceeds the available balance 97.00 EUR of merchant MERCH-001");
        assertThat(dsl.fetchCount(PAYOUTS)).isZero();
        assertThat(dsl.fetchCount(LEDGER_TRANSACTIONS, LEDGER_TRANSACTIONS.TYPE.eq("PAYOUT_HELD"))).isZero();
        assertThat(eurBalance()).isEqualTo(balance("97.00", "97.00", "0.00"));
    }

    @Test
    void secondPayoutSeesWhatTheFirstOneHolds() {
        requestPayout(http, eurPayout("PO-EXT-1", "60.00"));

        assertThat(postPayout(http, eurPayout("PO-EXT-2", "40.00")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(postPayout(http, eurPayout("PO-EXT-3", "37.00")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(eurBalance()).isEqualTo(balance("97.00", "0.00", "97.00"));
    }

    @Test
    void payoutInACurrencyTheMerchantHasNoBalanceInIsRejected() {
        ResponseEntity<Map<String, Object>> response =
                postPayout(http, payout("PO-EXT-1", "MERCH-001", "USD", "1.00", "2026-10-03T09:00:00+02:00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dsl.fetchCount(PAYOUTS)).isZero();
    }

    /** The scheduler and a "withdraw now" click at once: only what's available gets paid, never twice. */
    @Test
    void concurrentPayoutsNeverTakeMoreThanIsAvailable() throws Exception {
        int requests = 5;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<Map<String, Object>>>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                String externalId = "PO-EXT-C" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return postPayout(http, eurPayout(externalId, "40.00"));
                }));
            }
            start.countDown();
            List<HttpStatus> statuses = new ArrayList<>();
            for (Future<ResponseEntity<Map<String, Object>>> future : futures) {
                statuses.add(HttpStatus.valueOf(future.get(30, TimeUnit.SECONDS).getStatusCode().value()));
            }
            assertThat(statuses).filteredOn(HttpStatus.CREATED::equals).hasSize(2);
            assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(3);
        }

        assertThat(eurBalance()).isEqualTo(balance("97.00", "17.00", "80.00"));
    }

    @Test
    void repeatReturnsTheSamePayoutAndHoldsOnce() {
        Map<String, Object> first = postPayout(http, eurPayout("PO-EXT-1", "60.00")).getBody();

        ResponseEntity<Map<String, Object>> repeat = postPayout(http, eurPayout("PO-EXT-1", "60.0"));

        assertThat(repeat.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeat.getBody()).isEqualTo(first);
        assertThat(dsl.fetchCount(LEDGER_TRANSACTIONS, LEDGER_TRANSACTIONS.TYPE.eq("PAYOUT_HELD"))).isEqualTo(1);
        assertThat(eurBalance()).isEqualTo(balance("97.00", "37.00", "60.00"));
    }

    @Test
    void reusedExternalIdWithAnotherAmountIsAConflict() {
        requestPayout(http, eurPayout("PO-EXT-1", "60.00"));

        ResponseEntity<Map<String, Object>> response = postPayout(http, eurPayout("PO-EXT-1", "30.00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) response.getBody().get("detail")).contains("PO-EXT-1");
    }

    @Test
    void repeatedSettleReturnsTheSamePayoutAndPostsOnce() {
        String payoutId = requestPayout(http, eurPayout("PO-EXT-1", "60.00"));
        Map<String, Object> first = settle(http, payoutId, SETTLED_AT).getBody();

        ResponseEntity<Map<String, Object>> repeat = settle(http, payoutId, SETTLED_AT);

        assertThat(repeat.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repeat.getBody()).isEqualTo(first);
        assertThat(dsl.fetchCount(LEDGER_TRANSACTIONS, LEDGER_TRANSACTIONS.TYPE.eq("PAYOUT_SETTLED"))).isEqualTo(1);
    }

    @Test
    void payoutEndsOnlyOnce() {
        String settled = requestPayout(http, eurPayout("PO-EXT-1", "10.00"));
        String failed = requestPayout(http, eurPayout("PO-EXT-2", "10.00"));
        settle(http, settled, SETTLED_AT);
        fail(http, failed, SETTLED_AT);

        assertThat(fail(http, settled, SETTLED_AT).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(settle(http, failed, SETTLED_AT).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(settle(http, settled, "2026-10-06T10:00:00+02:00").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(eurBalance()).isEqualTo(balance("87.00", "87.00", "0.00"));
    }

    /** The case a hold can't prevent: a refund after the money has left. The merchant now owes us. */
    @Test
    void refundAfterASettledPayoutLeavesTheMerchantOwingUs() {
        String payoutId = requestPayout(http, eurPayout("PO-EXT-1", "97.00"));
        settle(http, payoutId, SETTLED_AT);

        recordRefund(http, paymentId, refund("RE-1", "2026-10-06T10:00:00+02:00", "59.50"));

        assertThat(eurBalance()).isEqualTo(balance("-48.50", "-48.50", "0.00"));
        assertThat(postPayout(http, eurPayout("PO-EXT-2", "0.01")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unknownOrMalformedPayoutIs404() {
        assertThat(settle(http, "PO-0000000000", SETTLED_AT).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(fail(http, "not-a-payout", SETTLED_AT).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidRequestsAre400() {
        assertThat(postPayout(http, payout("PO-EXT-1", "MERCH-999", "EUR", "1.00", "2026-10-03T09:00:00Z"))
                           .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(postPayout(http, with(eurPayout("PO-EXT-1", "1.00"), "amount", null)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(postPayout(http, eurPayout("PO-EXT-1", "1.001")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(postPayout(http, eurPayout("PO-EXT-1", "0")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(postPayout(http, with(eurPayout("PO-EXT-1", "1.00"), "currency", "EURO")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        String payoutId = requestPayout(http, eurPayout("PO-EXT-1", "1.00"));
        assertThat(settle(http, payoutId, "2026-10-03T06:59:59Z").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(settle(http, payoutId, "yesterday").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
