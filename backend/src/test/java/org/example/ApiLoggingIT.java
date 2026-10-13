package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.PaymentRequests.post;
import static org.example.PaymentRequests.validPayment;

/** One line per finished API request, and handled errors at WARN. */
@ExtendWith(OutputCaptureExtension.class)
class ApiLoggingIT extends IntegrationTestBase {

    private static String lineContaining(CapturedOutput output, String text) {
        return Arrays.stream(output.getOut().split("\\R"))
                .filter(line -> line.contains(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log line contains: " + text));
    }

    private void get(String uri) {
        http.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { /* only the log matters */ })
                .toBodilessEntity();
    }

    @Test
    void requestIsLoggedWithoutBodyAndResponseWithStatusAndBody(CapturedOutput output) {
        String paymentId = (String) post(http, validPayment()).getBody().get("paymentId");

        assertThat(lineContaining(output, "Request POST /payments"))
                .contains(" INFO ")
                .doesNotContain("EXT-1");
        assertThat(lineContaining(output, "Response POST /payments -> 201 in "))
                .contains(" INFO ")
                .contains("\"paymentId\":\"" + paymentId + "\"");
    }

    @Test
    void requestIsLoggedBeforeItIsHandled(CapturedOutput output) {
        post(http, Map.of("externalId", "EXT-1"));

        String out = output.getOut();
        assertThat(out.indexOf("Request POST /payments"))
                .isNotNegative()
                .isLessThan(out.indexOf("Rejected request: settledAt is required"));
    }

    @Test
    void queryStringIsLogged(CapturedOutput output) {
        get("/tax-statement?from=2026-07-01&to=2026-10-01");

        assertThat(lineContaining(output, "GET /tax-statement?from=2026-07-01&to=2026-10-01 -> 200"))
                .contains(" INFO ");
    }

    @Test
    void errorResponseIsLoggedWithItsBody(CapturedOutput output) {
        get("/merchants/MERCH-999/balances");

        assertThat(lineContaining(output, "GET /merchants/MERCH-999/balances -> 404"))
                .contains("\"detail\":");
    }

    @Test
    void apiDocsAreNotLogged(CapturedOutput output) {
        http.get().uri("/v3/api-docs").retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() {});

        assertThat(output.getOut()).doesNotContain("GET /v3/api-docs");
    }

    @Test
    void paymentErrorIsLoggedAtWarn(CapturedOutput output) {
        post(http, Map.of("externalId", "EXT-1"));

        assertThat(lineContaining(output, "Rejected request: settledAt is required")).contains(" WARN ");
    }

    @Test
    void merchantErrorIsLoggedAtWarn(CapturedOutput output) {
        get("/merchants/MERCH-999/balances");

        assertThat(lineContaining(output, "MerchantsExceptionHandler")).contains(" WARN ");
    }

    @Test
    void taxStatementErrorIsLoggedAtWarn(CapturedOutput output) {
        get("/tax-statement?from=2026-07-01");

        assertThat(lineContaining(output, "TaxStatementExceptionHandler")).contains(" WARN ");
    }

    @Test
    void frameworkErrorIsLoggedAtWarn(CapturedOutput output) {
        http.post()
                .uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{not json")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { /* only the log matters */ })
                .toBodilessEntity();

        assertThat(lineContaining(output, "ApiExceptionHandler")).contains(" WARN ");
    }
}
