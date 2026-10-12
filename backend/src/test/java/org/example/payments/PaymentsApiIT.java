package org.example.payments;

import org.example.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.jooq.Tables.PAYMENTS;
import static org.example.payments.PaymentRequests.post;
import static org.example.payments.PaymentRequests.validPayment;
import static org.example.payments.PaymentRequests.with;
import static org.example.payments.PaymentRequests.withContact;

class PaymentsApiIT extends IntegrationTestBase {

    @Test
    void newPaymentReturns201WithStoredPayment() {
        ResponseEntity<Map<String, Object>> response = post(http, validPayment());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat((String) body.get("paymentId")).matches("PAY-[0-9A-HJKMNP-TV-Z]{10}");
        assertThat(body).containsEntry("externalId", "EXT-1")
                .containsEntry("merchantId", "MERCH-001")
                .containsEntry("settledAt", "2026-09-30T21:30:00Z")
                .containsEntry("currency", "EUR")
                .containsEntry("totalAmount", "119.00")
                .containsEntry("taxRate", "0.19")
                .containsEntry("taxAmount", "19.00")
                .containsEntry("netAmount", "100.00")
                .containsEntry("feeAmount", "3.00")
                .containsEntry("merchantAmount", "97.00")
                .containsEntry("taxCountry", "DE");
        assertThat(body.get("customer")).isEqualTo(validPayment().get("customer"));
        assertThat(response.getHeaders().getLocation()).isNull();
        assertThat(dsl.fetchCount(PAYMENTS)).isEqualTo(1);
    }

    @Test
    void identicalRepeatReturnsSamePayment() {
        Object firstId = post(http, validPayment()).getBody().get("paymentId");

        ResponseEntity<Map<String, Object>> repeat = post(http, validPayment());

        assertThat(repeat.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeat.getBody()).containsEntry("paymentId", firstId);
        assertThat(dsl.fetchCount(PAYMENTS)).isEqualTo(1);
    }

    @Test
    void equivalentRepeatReturnsSamePayment() {
        Object firstId = post(http, validPayment()).getBody().get("paymentId");

        // Same amount with another scale; same instant written in UTC instead of +02:00.
        Map<String, Object> equivalent = with(with(validPayment(), "totalAmount", "119.0"),
                                              "settledAt", "2026-09-30T21:30:00Z");
        ResponseEntity<Map<String, Object>> repeat = post(http, equivalent);

        assertThat(repeat.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeat.getBody()).containsEntry("paymentId", firstId);
    }

    @Test
    void mismatchedRepeatReturns409() {
        post(http, validPayment());

        assertThat(post(http, with(validPayment(), "totalAmount", "120.00")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(post(http, with(validPayment(), "currency", "USD")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(post(http, with(validPayment(), "settledAt", "2026-09-30T23:31:00+02:00")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(dsl.fetchCount(PAYMENTS)).isEqualTo(1);
    }

    @Test
    void unknownMerchantReturns400() {
        ResponseEntity<Map<String, Object>> response = post(http, with(validPayment(), "merchantId", "MERCH-999"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("detail")).contains("Unknown merchant: MERCH-999");
    }

    @Test
    void merchantIdIsCaseSensitive() {
        assertThat(post(http, with(validPayment(), "merchantId", "merch-001")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    static Stream<Arguments> invalidRequests() {
        Map<String, Object> valid = validPayment();
        return Stream.of(
                Arguments.of("negative amount", with(valid, "totalAmount", "-1.00"), "totalAmount cannot be negative"),
                Arguments.of("non-numeric amount", with(valid, "totalAmount", "abc"), "totalAmount must be a decimal number"),
                Arguments.of("too many decimals", with(valid, "totalAmount", "10.001"), "more decimal places than EUR allows"),
                Arguments.of("amount too large", with(valid, "totalAmount", "1000000000000000"), "totalAmount is too large"),
                Arguments.of("tax rate as percent", with(valid, "taxRate", "19"), "taxRate must be a fraction below 1"),
                Arguments.of("unknown currency", with(valid, "currency", "EURO"), "currency must be an ISO 4217 code"),
                Arguments.of("lowercase currency", with(valid, "currency", "eur"), "currency must be an ISO 4217 code"),
                Arguments.of("lowercase country", withContact(valid, "countryCode", "de"), "ISO 3166-1 alpha-2"),
                Arguments.of("unknown country", withContact(valid, "countryCode", "XX"), "ISO 3166-1 alpha-2"),
                Arguments.of("settledAt without offset", with(valid, "settledAt", "2026-09-30T23:30:00"), "settledAt must be an ISO-8601 timestamp with an offset"),
                Arguments.of("settledAt beyond Postgres range", with(valid, "settledAt", "+300000-01-01T00:00:00Z"), "settledAt must be between years 0001 and 9999"),
                Arguments.of("blank merchantId", with(valid, "merchantId", "  "), "merchantId must not be blank"),
                Arguments.of("externalId too long", with(valid, "externalId", Stream.generate(() -> UUID.randomUUID().toString()).limit(600).collect(Collectors.joining())), "externalId must be at most 255 characters"),
                Arguments.of("missing externalId", with(valid, "externalId", null), "externalId is required"),
                Arguments.of("missing customer", with(valid, "customer", null), "customer is required"),
                Arguments.of("missing countryCode", withContact(valid, "countryCode", null), "customer.billingContact.countryCode is required"),
                Arguments.of("missing taxRate", with(valid, "taxRate", null), "taxRate is required"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void rejectsInvalidRequest(String description, Map<String, Object> body, String expectedDetail) {
        ResponseEntity<Map<String, Object>> response = post(http, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("detail")).contains(expectedDetail);
        assertThat(dsl.fetchCount(PAYMENTS)).isZero();
    }

    @Test
    void concurrentIdenticalRequestsRecordOnePayment() throws Exception {
        // Whether the requests actually overlap is up to the scheduler; the outcome must be the same either way.
        // RecordSettledPaymentUseCaseImplTest covers the retry path deterministically.
        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<Map<String, Object>>>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return post(http, validPayment());
                }));
            }
            start.countDown();

            Set<Object> paymentIds = new HashSet<>();
            for (Future<ResponseEntity<Map<String, Object>>> future : futures) {
                ResponseEntity<Map<String, Object>> response = future.get(30, TimeUnit.SECONDS);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                paymentIds.add(response.getBody().get("paymentId"));
            }
            assertThat(paymentIds).hasSize(1);
        }
        assertThat(dsl.fetchCount(PAYMENTS)).isEqualTo(1);
    }
}
