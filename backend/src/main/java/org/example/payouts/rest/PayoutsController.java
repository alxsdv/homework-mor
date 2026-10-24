package org.example.payouts.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.payouts.core.domain.PayoutId;
import org.example.payouts.core.ports.in.CompletePayoutCommand;
import org.example.payouts.core.ports.in.CompletePayoutUseCase;
import org.example.payouts.core.ports.in.PayoutNotFoundException;
import org.example.payouts.core.ports.in.RequestPayoutUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.example.payouts.rest.RequestFields.parseInstant;
import static org.example.payouts.rest.RequestFields.required;

/**
 * A payout is requested (its amount is held), then the bank's answer ends it: settle or fail.
 * Errors are mapped to ProblemDetail responses by PayoutsExceptionHandler.
 */
@RestController
@Tag(name = "Payouts")
public class PayoutsController {

    private final RequestPayoutUseCase requestPayoutUseCase;
    private final CompletePayoutUseCase completePayoutUseCase;

    public PayoutsController(RequestPayoutUseCase requestPayoutUseCase,
                             CompletePayoutUseCase completePayoutUseCase) {
        this.requestPayoutUseCase = requestPayoutUseCase;
        this.completePayoutUseCase = completePayoutUseCase;
    }

    /** A malformed id can't name a payout, so it's a 404 like an unknown one. */
    private static PayoutId parsePayoutId(String value) {
        try {
            return new PayoutId(value);
        } catch (IllegalArgumentException e) {
            throw new PayoutNotFoundException(value);
        }
    }

    /** Returns 201 with the payout, both for a new payout and for a matching repeat. */
    @PostMapping(path = "/payouts", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Request a payout to a merchant",
               description = "Holds the amount: it moves from the merchant's available balance to pending payouts. "
                             + "Repeating a request with the same externalId and body returns the same payout.")
    @ApiResponse(responseCode = "201", description = "The PENDING payout, new or matching a repeat")
    @ApiResponse(responseCode = "400", description = "Missing or malformed field, or unknown merchant")
    @ApiResponse(responseCode = "409", description = "The externalId is taken by a different payout, "
                                                     + "or the amount exceeds the available balance")
    @ApiResponse(responseCode = "503", description = "Could not generate a unique payout ID; retry")
    public ResponseEntity<PayoutResponse> requestPayout(@RequestBody PayoutRecord payoutRecord) {
        var payout = requestPayoutUseCase.requestPayout(payoutRecord.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(PayoutResponse.fromDomain(payout));
    }

    @PostMapping(path = "/payouts/{payoutId}/settle", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Record that the bank confirmed a payout",
               description = "The held amount leaves cash. Repeating the request returns the same payout.")
    @ApiResponse(responseCode = "200", description = "The SETTLED payout")
    @ApiResponse(responseCode = "400", description = "Missing or malformed field, or settledAt before requestedAt")
    @ApiResponse(responseCode = "404", description = "No payout with this ID")
    @ApiResponse(responseCode = "409", description = "The payout has already ended otherwise")
    public PayoutResponse settlePayout(
            @Parameter(description = "As returned by POST /payouts", example = "PO-0123456789")
            @PathVariable("payoutId") String payoutId,
            @RequestBody PayoutSettledRecord body) {
        Instant settledAt = parseInstant(required(body.settledAt(), "settledAt"), "settledAt");
        return PayoutResponse.fromDomain(
                completePayoutUseCase.settle(new CompletePayoutCommand(parsePayoutId(payoutId), settledAt)));
    }

    @PostMapping(path = "/payouts/{payoutId}/fail", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Record that the bank rejected a payout",
               description = "The held amount is available to the merchant again. "
                             + "Repeating the request returns the same payout.")
    @ApiResponse(responseCode = "200", description = "The FAILED payout")
    @ApiResponse(responseCode = "400", description = "Missing or malformed field, or failedAt before requestedAt")
    @ApiResponse(responseCode = "404", description = "No payout with this ID")
    @ApiResponse(responseCode = "409", description = "The payout has already ended otherwise")
    public PayoutResponse failPayout(
            @Parameter(description = "As returned by POST /payouts", example = "PO-0123456789")
            @PathVariable("payoutId") String payoutId,
            @RequestBody PayoutFailedRecord body) {
        Instant failedAt = parseInstant(required(body.failedAt(), "failedAt"), "failedAt");
        return PayoutResponse.fromDomain(
                completePayoutUseCase.fail(new CompletePayoutCommand(parsePayoutId(payoutId), failedAt)));
    }
}
