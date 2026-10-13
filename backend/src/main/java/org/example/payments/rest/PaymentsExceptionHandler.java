package org.example.payments.rest;

import org.example.payments.core.ports.in.InvalidPaymentException;
import org.example.payments.core.ports.in.PaymentConflictException;
import org.example.payments.core.ports.in.PaymentNotFoundException;
import org.example.payments.core.ports.in.RefundConflictException;
import org.example.payments.core.ports.in.RefundExceedsRefundableException;
import org.example.payments.core.ports.in.UnknownMerchantException;
import org.example.payments.core.ports.out.DuplicateExternalIdException;
import org.example.payments.core.ports.out.DuplicatePaymentIdException;
import org.example.payments.core.ports.out.DuplicateRefundExternalIdException;
import org.example.payments.core.ports.out.DuplicateRefundIdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/** Maps the payment and refund use cases' exceptions to ProblemDetail responses for PaymentsController. */
@RestControllerAdvice(assignableTypes = PaymentsController.class)
@Order(0) // Before the catch-all ApiExceptionHandler.
public class PaymentsExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(PaymentsExceptionHandler.class);

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, RuntimeException e) {
        String detail = Objects.requireNonNullElse(e.getMessage(), status.getReasonPhrase());
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }

    /** An id collision is random, so another attempt from the client can succeed. */
    private static ResponseEntity<ProblemDetail> retryLater(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                             .header(HttpHeaders.RETRY_AFTER, "1")
                             .body(problem);
    }

    @ExceptionHandler({InvalidPaymentException.class, UnknownMerchantException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException e) {
        logger.warn("Rejected request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(PaymentNotFoundException e) {
        logger.warn("{}", e.getMessage());
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler({PaymentConflictException.class, RefundConflictException.class,
                       RefundExceedsRefundableException.class})
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException e) {
        logger.warn("Conflicting request: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(DuplicatePaymentIdException.class)
    public ResponseEntity<ProblemDetail> handleDuplicatePaymentId(DuplicatePaymentIdException e) {
        logger.warn("Could not generate a unique payment ID, last attempt: {}", e.paymentId().value());
        return retryLater("Could not record the payment, please retry the request.");
    }

    @ExceptionHandler(DuplicateRefundIdException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateRefundId(DuplicateRefundIdException e) {
        logger.warn("Could not generate a unique refund ID, last attempt: {}", e.refundId().value());
        return retryLater("Could not record the refund, please retry the request.");
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

    /** As for payments: the retry should have found the concurrent refund, so this is a server defect. */
    @ExceptionHandler(DuplicateRefundExternalIdException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateRefundExternalId(DuplicateRefundExternalIdException e) {
        logger.error("Unique violation on refund external ID {} for merchant {} persisted after retries, "
                     + "but the refund lookup does not find it", e.externalId(), e.merchantId());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Could not record the refund.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
