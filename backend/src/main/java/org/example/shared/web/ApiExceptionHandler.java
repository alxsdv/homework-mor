package org.example.shared.web;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns the errors Spring MVC raises itself (malformed JSON, unsupported media type or method, unknown path)
 * and any exception no other handler maps into ProblemDetail responses.
 * <p>
 * Spring asks the advices in order and takes the first one with any matching handler, so this catch-all must
 * come last: the module handlers are ordered before it.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    // Not named logger: that would hide the commons-logging field ResponseEntityExceptionHandler declares.
    private static final Logger errorLogger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception e, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        if (statusCode.is5xxServerError()) {
            errorLogger.error("Request failed", e);
        } else {
            errorLogger.warn("Rejected request: {}", e.getMessage());
        }
        return super.handleExceptionInternal(e, body, headers, statusCode, request);
    }

    /** A bug, not something the client can fix, so the message stays in the log and out of the response. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        errorLogger.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                                                                    "Internal server error"));
    }
}
