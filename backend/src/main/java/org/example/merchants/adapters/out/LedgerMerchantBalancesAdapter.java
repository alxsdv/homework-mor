package org.example.merchants.adapters.out;

import org.example.ledger.core.domain.MerchantPayable;
import org.example.ledger.core.ports.in.GetMerchantPayablesUseCase;
import org.example.merchants.core.domain.MerchantBalance;
import org.example.merchants.core.ports.out.MerchantBalances;
import org.springframework.stereotype.Component;

import java.util.List;

/** The only place merchants talks to the ledger. */
@Component
public class LedgerMerchantBalancesAdapter implements MerchantBalances {

    private final GetMerchantPayablesUseCase getMerchantPayables;

    public LedgerMerchantBalancesAdapter(GetMerchantPayablesUseCase getMerchantPayables) {
        this.getMerchantPayables = getMerchantPayables;
    }

    private static MerchantBalance toMerchantBalance(MerchantPayable payable) {
        return new MerchantBalance(payable.currency(), payable.available(), payable.pendingPayout());
    }

    @Override
    public List<MerchantBalance> balancesOf(String merchantId) {
        return getMerchantPayables.getMerchantPayables(merchantId).stream()
                .map(LedgerMerchantBalancesAdapter::toMerchantBalance)
                .toList();
    }
}
