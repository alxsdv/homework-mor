package org.example.payments.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.payments.core.domain.Payment;
import org.example.payments.core.domain.Refund;
import org.example.payments.core.ports.in.RecordRefundUseCase;
import org.example.payments.core.ports.in.RecordSettledPaymentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Errors are mapped to ProblemDetail responses by PaymentsExceptionHandler. */
@RestController
@Tag(name = "Payments")
public class PaymentsController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentsController.class);

    private final RecordSettledPaymentUseCase recordSettledPaymentUseCase;
    private final RecordRefundUseCase recordRefundUseCase;

    public PaymentsController(RecordSettledPaymentUseCase recordSettledPaymentUseCase,
                              RecordRefundUseCase recordRefundUseCase) {
        this.recordSettledPaymentUseCase = recordSettledPaymentUseCase;
        this.recordRefundUseCase = recordRefundUseCase;
    }

    /** Returns 201 with the payment, both for a new payment and for a matching repeat. */
    @PostMapping(
            path = "/payments",
            consumes = "application/json",
            produces = "application/json")
    @Operation(summary = "Record a settled payment",
               description = "Repeating a request with the same externalId and body returns the same payment.")
    @ApiResponse(responseCode = "201", description = "The payment, new or matching a repeat")
    @ApiResponse(responseCode = "400", description = "Missing or malformed field, or unknown merchant")
    @ApiResponse(responseCode = "409", description = "The externalId is taken by a different payment")
    @ApiResponse(responseCode = "503", description = "Could not generate a unique payment ID; retry")
    public ResponseEntity<PaymentResponse> recordSettledPayment(@RequestBody PaymentRecord paymentRecord) {
        logger.info("POST /payments: {}", paymentRecord);
        Payment payment = recordSettledPaymentUseCase.recordSettledPayment(paymentRecord.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.fromDomain(payment));
    }

    /** Returns 201 with the refund, both for a new refund and for a matching repeat. */
    @PostMapping(
            path = "/payments/{paymentId}/refund",
            consumes = "application/json",
            produces = "application/json")
    @Operation(summary = "Refund a payment, fully or partly",
               description = "Repeating a request with the same externalId and body returns the same refund.")
    @ApiResponse(responseCode = "201", description = "The refund, new or matching a repeat")
    @ApiResponse(responseCode = "400", description = "Missing or malformed field")
    @ApiResponse(responseCode = "404", description = "No payment with this ID")
    @ApiResponse(responseCode = "409", description = "The externalId is taken by a different refund, "
                                                     + "or the amount exceeds what is still refundable")
    @ApiResponse(responseCode = "503", description = "Could not generate a unique refund ID; retry")
    public ResponseEntity<RefundResponse> recordRefund(
            @Parameter(description = "As returned by POST /payments", example = "PAY-0123456789")
            @PathVariable("paymentId") String paymentId,
            @RequestBody RefundRecord refundRecord) {
        logger.info("POST /payments/{}/refund: {}", paymentId, refundRecord);
        Refund refund = recordRefundUseCase.recordRefund(refundRecord.toCommand(paymentId));
        return ResponseEntity.status(HttpStatus.CREATED).body(RefundResponse.fromDomain(refund));
    }
}
