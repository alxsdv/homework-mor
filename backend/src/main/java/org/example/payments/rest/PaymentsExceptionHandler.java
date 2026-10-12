package org.example.payments.rest;

import org.example.payments.core.ports.in.InvalidPaymentException;
import org.example.payments.core.ports.in.PaymentConflictException;
import org.example.payments.core.ports.in.UnknownMerchantException;
import org.example.payments.core.ports.out.DuplicateExternalIdException;
import org.example.payments.core.ports.out.DuplicatePaymentIdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/** Maps the payment use case's exceptions to ProblemDetail responses for PaymentsController. */
@RestControllerAdvice(assignableTypes = PaymentsController.class)
public class PaymentsExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(PaymentsExceptionHandler.class);

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, RuntimeException e) {
        String detail = Objects.requireNonNullElse(e.getMessage(), status.getReasonPhrase());
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }

    @ExceptionHandler({InvalidPaymentException.class, UnknownMerchantException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException e) {
        logger.info("Rejected payment request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(PaymentConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(PaymentConflictException e) {
        logger.info("Conflicting payment request: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, e);
    }

    /** Payment IDs are random, so another attempt from the client can succeed. */
    @ExceptionHandler(DuplicatePaymentIdException.class)
    public ResponseEntity<ProblemDetail> handleDuplicatePaymentId(DuplicatePaymentIdException e) {
        logger.warn("Could not generate a unique payment ID, last attempt: {}", e.paymentId().value());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Could not record the payment, please retry the request.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                             .header(HttpHeaders.RETRY_AFTER, "1")
                             .body(problem);
    }

    /**
     * A retry finds the payment a concurrent request committed, so this surviving every retry means
     * the lookup and the unique constraint disagree: a server defect, not something the client can fix.
     */
    @ExceptionHandler(DuplicateExternalIdException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateExternalId(DuplicateExternalIdException e) {
        logger.error("Unique violation on external ID {} for merchant {} persisted after retries, "
                     + "but the payment lookup does not find it", e.externalId(), e.merchantId());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Could not record the payment.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
