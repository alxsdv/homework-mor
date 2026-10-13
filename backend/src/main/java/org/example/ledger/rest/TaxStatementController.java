package org.example.ledger.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.ledger.core.ports.in.GetTaxPayablesUseCase;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Errors are mapped to ProblemDetail responses by TaxStatementExceptionHandler. */
@RestController
@Tag(name = "Tax statement")
public class TaxStatementController {

    private final GetTaxPayablesUseCase getTaxPayables;

    public TaxStatementController(GetTaxPayablesUseCase getTaxPayables) {
        this.getTaxPayables = getTaxPayables;
    }

    /** Tax we owe per country and currency for [from, to), dates in zone (UTC if omitted). */
    @GetMapping(path = "/tax-statement", produces = "application/json")
    @Operation(summary = "Tax we owe per country and currency for a period")
    @ApiResponse(responseCode = "200", description = "The tax owed")
    @ApiResponse(responseCode = "400", description = "Missing or malformed parameter, or an empty period")
    public TaxStatementResponse getTaxStatement(
            @Parameter(required = true, description = "First day of the period, ISO date", example = "2026-07-01")
            @RequestParam(name = "from", required = false) @Nullable String from,
            @Parameter(required = true, description = "Day after the period ends, ISO date", example = "2026-10-01")
            @RequestParam(name = "to", required = false) @Nullable String to,
            @Parameter(description = "Time zone ID or offset the dates are in; UTC if left out",
                       example = "Europe/Berlin")
            @RequestParam(name = "zone", required = false) @Nullable String zone) {
        TaxPeriod period = TaxPeriod.parse(from, to, zone);
        return TaxStatementResponse.fromDomain(period, getTaxPayables.getTaxPayables(period.start(), period.end()));
    }
}
