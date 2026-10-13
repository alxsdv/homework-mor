package org.example;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.PaymentRequests.post;
import static org.example.PaymentRequests.validPayment;
import static org.example.PaymentRequests.with;

/** Every error, whether raised by a use case or by Spring itself, comes back as the same ProblemDetail shape. */
class ErrorResponsesIT extends IntegrationTestBase {

    private ResponseEntity<Map<String, Object>> postRaw(MediaType contentType, String body) {
        return http.post()
                .uri("/payments")
                .contentType(contentType)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { /* let the test assert on it */ })
                .toEntity(new ParameterizedTypeReference<>() {});
    }

    private static void assertProblem(ResponseEntity<Map<String, Object>> response, HttpStatus status,
                                      String instance) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody())
                .containsOnlyKeys("title", "status", "detail", "instance")
                .containsEntry("status", status.value())
                .containsEntry("instance", instance);
    }

    @Test
    void useCaseErrorIsProblemDetail() {
        assertProblem(post(http, with(validPayment(), "merchantId", "MERCH-999")),
                      HttpStatus.BAD_REQUEST, "/payments");
    }

    @Test
    void malformedJsonIsProblemDetail() {
        assertProblem(postRaw(MediaType.APPLICATION_JSON, "{not json"), HttpStatus.BAD_REQUEST, "/payments");
    }

    @Test
    void unsupportedContentTypeIsProblemDetail() {
        assertProblem(postRaw(MediaType.TEXT_PLAIN, "hello"), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "/payments");
    }

    @Test
    void unsupportedMethodIsProblemDetail() {
        ResponseEntity<Map<String, Object>> response = http.get()
                .uri("/payments")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, r) -> { /* let the test assert on it */ })
                .toEntity(new ParameterizedTypeReference<>() {});

        assertProblem(response, HttpStatus.METHOD_NOT_ALLOWED, "/payments");
    }

    @Test
    void unknownPathIsProblemDetail() {
        ResponseEntity<Map<String, Object>> response = http.get()
                .uri("/no-such-endpoint")
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, r) -> { /* let the test assert on it */ })
                .toEntity(new ParameterizedTypeReference<>() {});

        assertProblem(response, HttpStatus.NOT_FOUND, "/no-such-endpoint");
    }
}
