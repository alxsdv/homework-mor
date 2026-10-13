package org.example.merchants.core.services;

import org.example.merchants.core.domain.MerchantBalance;
import org.example.merchants.core.ports.in.GetMerchantBalancesUseCase;
import org.example.merchants.core.ports.in.MerchantNotFoundException;
import org.example.merchants.core.ports.out.MerchantBalances;
import org.example.merchants.core.ports.out.MerchantsRepository;

import java.util.List;

/** Two reads without a shared transaction: merchants are never deleted, so the existence check can't go stale. */
public class GetMerchantBalancesUseCaseImpl implements GetMerchantBalancesUseCase {

    private final MerchantsRepository merchantsRepository;
    private final MerchantBalances merchantBalances;

    public GetMerchantBalancesUseCaseImpl(MerchantsRepository merchantsRepository,
                                          MerchantBalances merchantBalances) {
        this.merchantsRepository = merchantsRepository;
        this.merchantBalances = merchantBalances;
    }

    @Override
    public List<MerchantBalance> getBalances(String merchantId) {
        if (merchantsRepository.findById(merchantId).isEmpty()) {
            throw new MerchantNotFoundException(merchantId);
        }
        return merchantBalances.balancesOf(merchantId);
    }
}
