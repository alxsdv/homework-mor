package org.example.ledger.core.ports.in;

import org.example.ledger.core.domain.MerchantPayable;

import java.util.List;

public interface GetMerchantPayablesUseCase {

    /**
     * One entry per currency the merchant has an account in, ordered by currency; empty if there are none.
     * Exact, case-sensitive match. The ledger doesn't know whether the merchant exists.
     */
    List<MerchantPayable> getMerchantPayables(String merchantId);
}
