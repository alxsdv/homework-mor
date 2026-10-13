package org.example.ledger.core.ports.in;

import org.example.ledger.core.domain.TaxPayable;

import java.time.Instant;
import java.util.List;

public interface GetTaxPayablesUseCase {

    /**
     * Tax posted by ledger transactions effective in [from, to), one entry per country and currency, ordered by
     * country, then currency. Throws IllegalArgumentException unless from is before to.
     */
    List<TaxPayable> getTaxPayables(Instant from, Instant to);
}
