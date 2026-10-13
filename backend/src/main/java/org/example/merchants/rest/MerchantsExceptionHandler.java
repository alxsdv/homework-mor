package org.example.merchants.rest;

import org.example.merchants.core.ports.in.MerchantNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/** Maps the merchants use cases' exceptions to ProblemDetail responses for MerchantsController. */
@RestControllerAdvice(assignableTypes = MerchantsController.class)
public class MerchantsExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(MerchantsExceptionHandler.class);

    @ExceptionHandler(MerchantNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(MerchantNotFoundException e) {
        logger.info("Merchant not found: {}", e.getMessage());
        String detail = Objects.requireNonNullElse(e.getMessage(), HttpStatus.NOT_FOUND.getReasonPhrase());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                             .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail));
    }
}
