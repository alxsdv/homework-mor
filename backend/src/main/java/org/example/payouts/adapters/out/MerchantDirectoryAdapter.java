package org.example.payouts.adapters.out;

import org.example.merchants.core.ports.in.FindMerchantUseCase;
import org.example.payouts.core.ports.out.MerchantDirectory;
import org.springframework.stereotype.Component;

/** The only place payouts talks to the merchants module. */
@Component
public class MerchantDirectoryAdapter implements MerchantDirectory {

    private final FindMerchantUseCase findMerchant;

    public MerchantDirectoryAdapter(FindMerchantUseCase findMerchant) {
        this.findMerchant = findMerchant;
    }

    @Override
    public boolean exists(String merchantId) {
        return findMerchant.findById(merchantId).isPresent();
    }
}
