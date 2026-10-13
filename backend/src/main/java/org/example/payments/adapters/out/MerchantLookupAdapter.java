package org.example.payments.adapters.out;

import org.example.merchants.core.ports.in.FindMerchantUseCase;
import org.example.payments.core.domain.Merchant;
import org.example.payments.core.ports.out.MerchantLookup;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** The only place payments talks to the merchants module: copies what payments needs into its own Merchant. */
@Component
public class MerchantLookupAdapter implements MerchantLookup {

    private final FindMerchantUseCase findMerchant;

    public MerchantLookupAdapter(FindMerchantUseCase findMerchant) {
        this.findMerchant = findMerchant;
    }

    private static Merchant toPaymentsMerchant(org.example.merchants.core.domain.Merchant merchant) {
        return new Merchant(merchant.id(), merchant.feeRate());
    }

    @Override
    public Optional<Merchant> findById(String merchantId) {
        return findMerchant.findById(merchantId).map(MerchantLookupAdapter::toPaymentsMerchant);
    }
}
