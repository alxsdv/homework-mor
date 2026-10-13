package org.example.ledger.core.ports.out;

import org.example.ledger.core.domain.MerchantPayable;
import org.example.ledger.core.domain.TaxPayable;

import java.time.Instant;
import java.util.List;

/** Read side of the ledger. Amounts are positive when we owe them, at the currency's scale. */
public interface LedgerQueries {

    List<MerchantPayable> merchantPayables(String merchantId);

    /** Effective in [from, to). */
    List<TaxPayable> taxPayables(Instant from, Instant to);
}
