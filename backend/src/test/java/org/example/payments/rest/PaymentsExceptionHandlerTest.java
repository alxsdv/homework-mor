package org.example.payments.rest;

import org.example.PaymentRequests;
import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.domain.RefundId;
import org.example.payments.core.ports.in.RecordRefundUseCase;
import org.example.payments.core.ports.in.RecordSettledPaymentUseCase;
import org.example.payments.core.ports.in.RefundConflictException;
import org.example.payments.core.ports.in.RefundExceedsRefundableException;
import org.example.payments.core.ports.out.DuplicateExternalIdException;
import org.example.payments.core.ports.out.DuplicatePaymentIdException;
import org.example.payments.core.ports.out.DuplicateRefundExternalIdException;
import org.example.payments.core.ports.out.DuplicateRefundIdException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mappings that need a use case to fail in a way a real request can't easily trigger. Of the retries exhausted
 * in the use cases, only an id collision is worth retrying from the client.
 */
class PaymentsExceptionHandlerTest {

    private static final RecordSettledPaymentUseCase NO_PAYMENTS = command -> {
        throw new IllegalStateException("not expected in this test");
    };
    private static final RecordRefundUseCase NO_REFUNDS = command -> {
        throw new IllegalStateException("not expected in this test");
    };
    private static final String REFUND_BODY = """
            {"externalId": "RE-1", "refundedAt": "2026-10-02T10:15:00+02:00", "refundAmount": "59.50"}""";

    private static MockMvc mvc(RecordSettledPaymentUseCase payments, RecordRefundUseCase refunds) {
        return MockMvcBuilders.standaloneSetup(new PaymentsController(payments, refunds))
                .setControllerAdvice(new PaymentsExceptionHandler())
                .build();
    }

    private static ResultActions postPaymentWith(RecordSettledPaymentUseCase useCase) throws Exception {
        return mvc(useCase, NO_REFUNDS).perform(post("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.shared().writeValueAsString(PaymentRequests.validPayment())));
    }

    private static ResultActions postRefundWith(String paymentId, RecordRefundUseCase useCase) throws Exception {
        return mvc(NO_PAYMENTS, useCase).perform(post("/payments/{paymentId}/refund", paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REFUND_BODY));
    }

    @Test
    void paymentIdCollisionReturns503WithRetryAfter() throws Exception {
        postPaymentWith(command -> {
            throw new DuplicatePaymentIdException(new PaymentId("PAY-0000000001"));
        })
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.detail").value("Could not record the payment, please retry the request."));
    }

    @Test
    void externalIdCollisionAfterRetriesReturns500WithoutRetryAfter() throws Exception {
        postPaymentWith(command -> {
            throw new DuplicateExternalIdException(command.merchantId(), command.externalId());
        })
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.detail").value("Could not record the payment."));
    }

    @Test
    void malformedPaymentIdReturns404WithoutCallingTheUseCase() throws Exception {
        postRefundWith("nope", NO_REFUNDS)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Payment not found: nope"));
    }

    @Test
    void refundBeyondWhatIsLeftReturns409() throws Exception {
        postRefundWith("PAY-0000000001", command -> {
            throw new RefundExceedsRefundableException(command.paymentId(), new BigDecimal("59.50"),
                                                       new BigDecimal("40.00"));
        })
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Refund amount 59.50 exceeds the refundable amount 40.00 of payment PAY-0000000001"));
    }

    @Test
    void mismatchedRefundRepeatReturns409() throws Exception {
        postRefundWith("PAY-0000000001", command -> {
            throw new RefundConflictException("MERCH-001", command.externalId());
        })
                .andExpect(status().isConflict());
    }

    @Test
    void refundIdCollisionReturns503WithRetryAfter() throws Exception {
        postRefundWith("PAY-0000000001", command -> {
            throw new DuplicateRefundIdException(new RefundId("REF-0000000001"));
        })
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.detail").value("Could not record the refund, please retry the request."));
    }

    @Test
    void refundExternalIdCollisionAfterRetriesReturns500WithoutRetryAfter() throws Exception {
        postRefundWith("PAY-0000000001", command -> {
            throw new DuplicateRefundExternalIdException("MERCH-001", command.externalId());
        })
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.detail").value("Could not record the refund."));
    }
}
