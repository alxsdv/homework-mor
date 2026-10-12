package org.example.payments.rest;

import org.example.payments.core.domain.Payment;
import org.example.payments.core.ports.in.RecordSettledPaymentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Errors are mapped to ProblemDetail responses by PaymentsExceptionHandler. */
@RestController
public class PaymentsController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentsController.class);

    private final RecordSettledPaymentUseCase recordSettledPaymentUseCase;

    public PaymentsController(RecordSettledPaymentUseCase recordSettledPaymentUseCase) {
        this.recordSettledPaymentUseCase = recordSettledPaymentUseCase;
    }

    /** Returns 201 with the payment, both for a new payment and for a matching repeat. */
    @PostMapping(
            path = "/payments",
            consumes = "application/json",
            produces = "application/json")
    public ResponseEntity<PaymentResponse> recordSettledPayment(@RequestBody PaymentRecord paymentRecord) {
        logger.info("POST /payments: {}", paymentRecord);
        Payment payment = recordSettledPaymentUseCase.recordSettledPayment(paymentRecord.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.fromDomain(payment));
    }
}
