package org.example.payments.rest;

import org.example.PaymentRequests;
import org.example.payments.core.domain.PaymentId;
import org.example.payments.core.ports.in.RecordSettledPaymentUseCase;
import org.example.payments.core.ports.out.DuplicateExternalIdException;
import org.example.payments.core.ports.out.DuplicatePaymentIdException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Retries exhausted in the use case: only the payment-ID collision is worth retrying from the client. */
class PaymentsExceptionHandlerTest {

    private static ResultActions postPaymentWith(RecordSettledPaymentUseCase useCase) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PaymentsController(useCase))
                .setControllerAdvice(new PaymentsExceptionHandler())
                .build();
        return mvc.perform(post("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.shared().writeValueAsString(PaymentRequests.validPayment())));
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
}
