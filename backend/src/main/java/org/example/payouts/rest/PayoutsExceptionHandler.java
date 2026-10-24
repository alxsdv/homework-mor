package org.example.payouts.rest;

import org.example.payouts.core.ports.in.InvalidPayoutException;
import org.example.payouts.core.ports.in.PayoutAlreadyCompletedException;
import org.example.payouts.core.ports.in.PayoutConflictException;
import org.example.payouts.core.ports.in.PayoutExceedsAvailableException;
import org.example.payouts.core.ports.in.PayoutNotFoundException;
import org.example.payouts.core.ports.in.UnknownPayoutMerchantException;
import org.example.payouts.core.ports.out.DuplicatePayoutExternalIdException;
import org.example.payouts.core.ports.out.DuplicatePayoutIdException;
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

/** Maps the payout use cases' exceptions to ProblemDetail responses for PayoutsController. */
@RestControllerAdvice(assignableTypes = PayoutsController.class)
@Order(0) // Before the catch-all ApiExceptionHandler.
public class PayoutsExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(PayoutsExceptionHandler.class);

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, RuntimeException e) {
        String detail = Objects.requireNonNullElse(e.getMessage(), status.getReasonPhrase());
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }

    @ExceptionHandler({InvalidPayoutException.class, UnknownPayoutMerchantException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException e) {
        logger.warn("Rejected request: {}", e.getMessage());
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(PayoutNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(PayoutNotFoundException e) {
        logger.warn("{}", e.getMessage());
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler({PayoutConflictException.class, PayoutAlreadyCompletedException.class,
                       PayoutExceedsAvailableException.class})
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException e) {
        logger.warn("Conflicting request: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, e);
    }

    /** An id collision is random, so another attempt from the client can succeed. */
    @ExceptionHandler(DuplicatePayoutIdException.class)
    public ResponseEntity<ProblemDetail> handleDuplicatePayoutId(DuplicatePayoutIdException e) {
        logger.warn("Could not generate a unique payout ID, last attempt: {}", e.payoutId().value());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Could not record the payout, please retry the request.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                             .header(HttpHeaders.RETRY_AFTER, "1")
                             .body(problem);
    }

    /** A retry finds the payout a concurrent request committed, so this surviving the retries is a server defect. */
    @ExceptionHandler(DuplicatePayoutExternalIdException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateExternalId(DuplicatePayoutExternalIdException e) {
        logger.error("Unique violation on payout external ID {} for merchant {} persisted after retries, "
                     + "but the payout lookup does not find it", e.externalId(), e.merchantId());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Could not record the payout.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
