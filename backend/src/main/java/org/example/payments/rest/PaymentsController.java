package org.example.payments.rest;

import org.example.payments.core.ports.in.RecordSettledPaymentUseCase;
import org.example.payments.core.ports.out.DuplicatePaymentIdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentsController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentsController.class);

    private final RecordSettledPaymentUseCase recordSettledPaymentUseCase;

    public PaymentsController(RecordSettledPaymentUseCase recordSettledPaymentUseCase) {
        this.recordSettledPaymentUseCase = recordSettledPaymentUseCase;
    }

    @PostMapping(
            path = "/payments",
            consumes = "application/json",
            produces = "application/json")
    public ResponseEntity<?> recordSettledPayment(@RequestBody PaymentRecord paymentRecord) {
        logger.info("POST /payments: {}", paymentRecord);
        recordSettledPaymentUseCase.recordSettledPayment(paymentRecord.toCommand());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(DuplicatePaymentIdException.class)
    public ResponseEntity<ProblemDetail> handleDuplicatePaymentId(DuplicatePaymentIdException e) {
        logger.warn("Could not generate a unique payment ID, last attempt: {}", e.paymentId().value());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Could not record the payment, please retry the request.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                             .header(HttpHeaders.RETRY_AFTER, "1")
                             .body(problem);
    }
}
