package org.example.payments;

import org.example.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.PaymentRequests.postRefund;
import static org.example.PaymentRequests.recordPayment;
import static org.example.PaymentRequests.recordRefund;
import static org.example.PaymentRequests.refund;
import static org.example.PaymentRequests.validPayment;
import static org.example.PaymentRequests.with;
import static org.example.jooq.Tables.REFUNDS;
import static org.jooq.impl.DSL.sum;

/** The reference payment is 119.00 EUR at 19% tax and a 3% fee: tax 19.00, net 100.00, fee 3.00, merchant 97.00. */
class RefundsApiIT extends IntegrationTestBase {

    private static final String REFUNDED_AT = "2026-10-02T10:15:00+02:00";

    /** Half of the reference payment: tax 9.50, net 50.00, fee 1.50, merchant 48.50. */
    private static Map<String, Object> halfRefund() {
        return refund("RE-1", REFUNDED_AT, "59.50");
    }

    @Test
    void newRefundReturns201WithStoredRefund() {
        String paymentId = recordPayment(http, validPayment());

        ResponseEntity<Map<String, Object>> response = postRefund(http, paymentId, halfRefund());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat((String) body.get("refundId")).matches("REF-[0-9A-HJKMNP-TV-Z]{10}");
        assertThat(body).containsEntry("externalId", "RE-1")
                .containsEntry("paymentId", paymentId)
                .containsEntry("merchantId", "MERCH-001")
                .containsEntry("refundedAt", "2026-10-02T08:15:00Z")
                .containsEntry("currency", "EUR")
                .containsEntry("refundAmount", "59.50")
                .containsEntry("taxRate", "0.19")
                .containsEntry("taxAmount", "9.50")
                .containsEntry("netAmount", "50.00")
                .containsEntry("feeAmount", "1.50")
                .containsEntry("merchantAmount", "48.50")
                .containsEntry("taxCountry", "DE");
        assertThat(response.getHeaders().getLocation()).isNull();
        assertThat(dsl.fetchCount(REFUNDS)).isEqualTo(1);
    }

    @Test
    void absentAmountRefundsWhatIsLeft() {
        String paymentId = recordPayment(http, validPayment());
        recordRefund(http, paymentId, halfRefund());

        Map<String, Object> rest = recordRefund(http, paymentId, refund("RE-2", REFUNDED_AT, null));

        assertThat(rest).containsEntry("refundAmount", "59.50")
                .containsEntry("taxAmount", "9.50")
                .containsEntry("netAmount", "50.00")
                .containsEntry("feeAmount", "1.50")
                .containsEntry("merchantAmount", "48.50");
    }

    @Test
    void identicalRepeatReturnsSameRefund() {
        String paymentId = recordPayment(http, validPayment());
        Object firstId = recordRefund(http, paymentId, halfRefund()).get("refundId");

        ResponseEntity<Map<String, Object>> repeat = postRefund(http, paymentId, halfRefund());

        assertThat(repeat.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeat.getBody()).containsEntry("refundId", firstId);
        assertThat(dsl.fetchCount(REFUNDS)).isEqualTo(1);
    }

    @Test
    void equivalentRepeatReturnsSameRefund() {
        String paymentId = recordPayment(http, validPayment());
        Object firstId = recordRefund(http, paymentId, halfRefund()).get("refundId");

        // Same amount with another scale; same instant written in UTC instead of +02:00.
        ResponseEntity<Map<String, Object>> repeat =
                postRefund(http, paymentId, refund("RE-1", "2026-10-02T08:15:00Z", "59.5"));

        assertThat(repeat.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeat.getBody()).containsEntry("refundId", firstId);
    }

    @Test
    void repeatedFullRefundReturnsSameRefundThoughNothingIsLeft() {
        String paymentId = recordPayment(http, validPayment());
        Object firstId = recordRefund(http, paymentId, refund("RE-1", REFUNDED_AT, null)).get("refundId");

        ResponseEntity<Map<String, Object>> repeat = postRefund(http, paymentId, refund("RE-1", REFUNDED_AT, null));

        assertThat(repeat.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeat.getBody()).containsEntry("refundId", firstId).containsEntry("refundAmount", "119.00");
    }

    @Test
    void mismatchedRepeatReturns409() {
        String paymentId = recordPayment(http, validPayment());
        String otherPaymentId = recordPayment(http, with(validPayment(), "externalId", "EXT-2"));
        recordRefund(http, paymentId, halfRefund());

        assertThat(postRefund(http, otherPaymentId, halfRefund()).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(postRefund(http, paymentId, refund("RE-1", "2026-10-02T10:16:00+02:00", "59.50")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(postRefund(http, paymentId, refund("RE-1", REFUNDED_AT, "59.51")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(postRefund(http, paymentId, refund("RE-1", REFUNDED_AT, null)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(dsl.fetchCount(REFUNDS)).isEqualTo(1);
    }

    @Test
    void sameExternalIdForAnotherMerchantIsANewRefund() {
        String paymentId = recordPayment(http, validPayment());
        String otherMerchantsPaymentId = recordPayment(http, with(validPayment(), "merchantId", "MERCH-002"));

        Object first = recordRefund(http, paymentId, halfRefund()).get("refundId");
        Object second = recordRefund(http, otherMerchantsPaymentId, halfRefund()).get("refundId");

        assertThat(second).isNotEqualTo(first);
        assertThat(dsl.fetchCount(REFUNDS)).isEqualTo(2);
    }

    @Test
    void refundBeyondWhatIsLeftReturns409() {
        String paymentId = recordPayment(http, validPayment());

        ResponseEntity<Map<String, Object>> tooMuch = postRefund(http, paymentId, refund("RE-1", REFUNDED_AT, "119.01"));
        assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) tooMuch.getBody().get("detail")).contains("exceeds the refundable amount 119.00");

        recordRefund(http, paymentId, refund("RE-2", REFUNDED_AT, null));
        ResponseEntity<Map<String, Object>> nothingLeft =
                postRefund(http, paymentId, refund("RE-3", REFUNDED_AT, null));
        assertThat(nothingLeft.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) nothingLeft.getBody().get("detail")).contains("has nothing left to refund");
        assertThat(dsl.fetchCount(REFUNDS)).isEqualTo(1);
    }

    @Test
    void unknownOrMalformedPaymentIdReturns404() {
        String paymentId = recordPayment(http, validPayment());

        for (String id : List.of("PAY-0000000000", paymentId.toLowerCase(Locale.ROOT), "nope")) {
            ResponseEntity<Map<String, Object>> response = postRefund(http, id, halfRefund());

            assertThat(response.getStatusCode()).as(id).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat((String) response.getBody().get("detail")).isEqualTo("Payment not found: " + id);
        }
        assertThat(dsl.fetchCount(REFUNDS)).isZero();
    }

    static Stream<Arguments> invalidRequests() {
        Map<String, Object> valid = refund("RE-1", REFUNDED_AT, "10.00");
        return Stream.of(
                Arguments.of("missing externalId", with(valid, "externalId", null), "externalId is required"),
                Arguments.of("blank externalId", with(valid, "externalId", "  "), "externalId must not be blank"),
                Arguments.of("externalId too long", with(valid, "externalId", "E".repeat(256)), "externalId must be at most 255 characters"),
                Arguments.of("missing refundedAt", with(valid, "refundedAt", null), "refundedAt is required"),
                Arguments.of("refundedAt without offset", with(valid, "refundedAt", "2026-10-02T10:15:00"), "refundedAt must be an ISO-8601 timestamp with an offset"),
                Arguments.of("refundedAt beyond Postgres range", with(valid, "refundedAt", "+300000-01-01T00:00:00Z"), "refundedAt must be between years 0001 and 9999"),
                Arguments.of("refundedAt before settledAt", with(valid, "refundedAt", "2026-09-30T21:29:59Z"), "refundedAt must not be before the payment's settledAt"),
                Arguments.of("non-numeric amount", with(valid, "refundAmount", "abc"), "refundAmount must be a decimal number"),
                Arguments.of("zero amount", with(valid, "refundAmount", "0.00"), "refundAmount must be positive"),
                Arguments.of("negative amount", with(valid, "refundAmount", "-1.00"), "refundAmount must be positive"),
                Arguments.of("too many decimals", with(valid, "refundAmount", "10.001"), "more decimal places than EUR allows"),
                Arguments.of("amount too large", with(valid, "refundAmount", "1000000000000000"), "refundAmount is too large"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void rejectsInvalidRequest(String description, Map<String, Object> body, String expectedDetail) {
        String paymentId = recordPayment(http, validPayment());

        ResponseEntity<Map<String, Object>> response = postRefund(http, paymentId, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("detail")).contains(expectedDetail);
        assertThat(dsl.fetchCount(REFUNDS)).isZero();
    }

    @Test
    void concurrentRefundsCannotTogetherExceedThePayment() throws Exception {
        // Any two of these 60.00 refunds exceed the 119.00 payment, so the payment lock must let exactly one in.
        String paymentId = recordPayment(http, validPayment());
        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpStatus>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                Map<String, Object> body = refund("RE-C" + i, REFUNDED_AT, "60.00");
                futures.add(pool.submit(() -> {
                    start.await();
                    return HttpStatus.valueOf(postRefund(http, paymentId, body).getStatusCode().value());
                }));
            }
            start.countDown();

            List<HttpStatus> statuses = new ArrayList<>();
            for (Future<HttpStatus> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnlyOnce(HttpStatus.CREATED);
            assertThat(statuses).filteredOn(status -> status != HttpStatus.CREATED)
                    .containsOnly(HttpStatus.CONFLICT);
        }
        assertThat(dsl.select(sum(REFUNDS.TOTAL_AMOUNT)).from(REFUNDS).fetchSingle().value1())
                .isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    void concurrentIdenticalRequestsRecordOneRefund() throws Exception {
        String paymentId = recordPayment(http, validPayment());
        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<Map<String, Object>>>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return postRefund(http, paymentId, halfRefund());
                }));
            }
            start.countDown();

            Set<Object> refundIds = new HashSet<>();
            for (Future<ResponseEntity<Map<String, Object>>> future : futures) {
                ResponseEntity<Map<String, Object>> response = future.get(30, TimeUnit.SECONDS);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                refundIds.add(response.getBody().get("refundId"));
            }
            assertThat(refundIds).hasSize(1);
        }
        assertThat(dsl.fetchCount(REFUNDS)).isEqualTo(1);
    }

    @Test
    void concurrentSameExternalIdOnTwoPaymentsRecordsOneRefund() throws Exception {
        // Locks on two different payments don't serialize these requests, so the losers reach the
        // merchant + externalId unique key, retry and then find the winner's refund of another payment.
        List<String> paymentIds = List.of(recordPayment(http, validPayment()),
                                          recordPayment(http, with(validPayment(), "externalId", "EXT-2")));
        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<Map<String, Object>>>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                String paymentId = paymentIds.get(i % 2);
                futures.add(pool.submit(() -> {
                    start.await();
                    return postRefund(http, paymentId, halfRefund());
                }));
            }
            start.countDown();

            Set<Object> refundIds = new HashSet<>();
            int conflicts = 0;
            for (Future<ResponseEntity<Map<String, Object>>> future : futures) {
                ResponseEntity<Map<String, Object>> response = future.get(30, TimeUnit.SECONDS);
                if (response.getStatusCode() == HttpStatus.CONFLICT) {
                    conflicts++;
                } else {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    refundIds.add(response.getBody().get("refundId"));
                }
            }
            assertThat(refundIds).hasSize(1);
            // Every request for the other payment is rejected.
            assertThat(conflicts).isEqualTo(requests / 2);
        }
        assertThat(dsl.fetchCount(REFUNDS)).isEqualTo(1);
    }
}
