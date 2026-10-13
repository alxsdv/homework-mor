package org.example.ledger.rest;

import org.example.ledger.core.ports.in.GetTaxPayablesUseCase;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Errors are mapped to ProblemDetail responses by TaxStatementExceptionHandler. */
@RestController
public class TaxStatementController {

    private final GetTaxPayablesUseCase getTaxPayables;

    public TaxStatementController(GetTaxPayablesUseCase getTaxPayables) {
        this.getTaxPayables = getTaxPayables;
    }

    /** Tax we owe per country and currency for [from, to), dates in zone (UTC if omitted). */
    @GetMapping(path = "/tax-statement", produces = "application/json")
    public TaxStatementResponse getTaxStatement(
            @RequestParam(name = "from", required = false) @Nullable String from,
            @RequestParam(name = "to", required = false) @Nullable String to,
            @RequestParam(name = "zone", required = false) @Nullable String zone) {
        TaxPeriod period = TaxPeriod.parse(from, to, zone);
        return TaxStatementResponse.fromDomain(period, getTaxPayables.getTaxPayables(period.start(), period.end()));
    }
}
