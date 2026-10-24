package org.example;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/** The generated OpenAPI spec and the Swagger UI that renders it. */
class OpenApiIT extends IntegrationTestBase {

    private Map<String, Object> fetchSpec() {
        return http.get().uri("/v3/api-docs").retrieve().body(new ParameterizedTypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> at(Map<String, Object> node, String... keys) {
        Map<String, Object> current = node;
        for (String key : keys) {
            assertThat(current).containsKey(key);
            current = (Map<String, Object>) current.get(key);
        }
        return current;
    }

    @Test
    void specListsEveryEndpoint() {
        assertThat(at(fetchSpec(), "paths")).containsOnlyKeys(
                "/payments", "/payments/{paymentId}/refund", "/merchants/{merchantId}/balances", "/tax-statement",
                "/payouts", "/payouts/{payoutId}/settle", "/payouts/{payoutId}/fail");
    }

    @Test
    void postsAreDocumentedAsCreated() {
        Map<String, Object> paths = at(fetchSpec(), "paths");

        assertThat(at(paths, "/payments", "post", "responses")).containsKey("201").doesNotContainKey("200");
        assertThat(at(paths, "/payments/{paymentId}/refund", "post", "responses"))
                .containsKey("201").doesNotContainKey("200");
        assertThat(at(paths, "/payouts", "post", "responses")).containsKey("201").doesNotContainKey("200");
    }

    @Test
    void errorResponsesAreProblemDetails() {
        Map<String, Object> spec = fetchSpec();
        Map<String, Object> responses = at(spec, "paths", "/payments", "post", "responses");

        assertThat(responses).containsKeys("400", "409", "503");
        assertThat(at(responses, "400", "content")).containsOnlyKeys("application/problem+json");
        // Spring leaves type out of the JSON when it is not set, and no handler sets it.
        assertThat(at(spec, "components", "schemas", "ProblemDetail", "properties"))
                .containsOnlyKeys("title", "status", "detail", "instance");
    }

    @Test
    void requestSchemasMarkWhatTheEndpointsRequire() {
        Map<String, Object> schemas = at(fetchSpec(), "components", "schemas");

        assertThat(at(schemas, "PaymentRecord").get("required")).asInstanceOf(LIST)
                .containsExactlyInAnyOrder(
                        "externalId", "settledAt", "merchantId", "customer", "currency", "totalAmount", "taxRate");
        assertThat(at(schemas, "Contact").get("required")).asInstanceOf(LIST)
                .containsExactlyInAnyOrder("addressLine1", "countryCode", "postalCode");
        assertThat(at(schemas, "RefundRecord").get("required")).asInstanceOf(LIST)
                .containsExactlyInAnyOrder("externalId", "refundedAt");
        assertThat(at(schemas, "PayoutRecord").get("required")).asInstanceOf(LIST)
                .containsExactlyInAnyOrder("externalId", "merchantId", "currency", "amount", "requestedAt");
    }

    @Test
    void swaggerUiIsServed() {
        ResponseEntity<String> response = http.get().uri("/swagger-ui/index.html").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Swagger UI");
    }
}
