package org.example.ledger.core.services;

import org.example.ledger.core.domain.MerchantPayable;
import org.example.ledger.core.ports.in.GetMerchantPayablesUseCase;
import org.example.ledger.core.ports.out.LedgerQueries;

import java.util.List;

public class GetMerchantPayablesUseCaseImpl implements GetMerchantPayablesUseCase {

    private final LedgerQueries ledgerQueries;

    public GetMerchantPayablesUseCaseImpl(LedgerQueries ledgerQueries) {
        this.ledgerQueries = ledgerQueries;
    }

    @Override
    public List<MerchantPayable> getMerchantPayables(String merchantId) {
        return ledgerQueries.merchantPayables(merchantId);
    }
}
