package org.example.ledger.core.services;

import org.example.ledger.core.domain.TaxPayable;
import org.example.ledger.core.ports.in.GetTaxPayablesUseCase;
import org.example.ledger.core.ports.out.LedgerQueries;

import java.time.Instant;
import java.util.List;

public class GetTaxPayablesUseCaseImpl implements GetTaxPayablesUseCase {

    private final LedgerQueries ledgerQueries;

    public GetTaxPayablesUseCaseImpl(LedgerQueries ledgerQueries) {
        this.ledgerQueries = ledgerQueries;
    }

    @Override
    public List<TaxPayable> getTaxPayables(Instant from, Instant to) {
        // Callers validate the period first, so an empty or reversed one is a programming error.
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("Period start " + from + " must be before its end " + to);
        }
        return ledgerQueries.taxPayables(from, to);
    }
}
