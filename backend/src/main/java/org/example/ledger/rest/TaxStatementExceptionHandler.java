package org.example.ledger.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/** Maps invalid tax periods to ProblemDetail responses for TaxStatementController. */
@RestControllerAdvice(assignableTypes = TaxStatementController.class)
public class TaxStatementExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(TaxStatementExceptionHandler.class);

    @ExceptionHandler(InvalidTaxPeriodException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPeriod(InvalidTaxPeriodException e) {
        logger.info("Rejected tax statement request: {}", e.getMessage());
        String detail = Objects.requireNonNullElse(e.getMessage(), HttpStatus.BAD_REQUEST.getReasonPhrase());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                             .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }
}
