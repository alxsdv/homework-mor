package org.example.merchants.core.services;

import org.example.merchants.core.domain.Merchant;
import org.example.merchants.core.ports.in.FindMerchantUseCase;
import org.example.merchants.core.ports.out.MerchantsRepository;

import java.util.Optional;

/** A single read: no transaction of its own, so a caller's transaction is joined. */
public class FindMerchantUseCaseImpl implements FindMerchantUseCase {

    private final MerchantsRepository merchantsRepository;

    public FindMerchantUseCaseImpl(MerchantsRepository merchantsRepository) {
        this.merchantsRepository = merchantsRepository;
    }

    @Override
    public Optional<Merchant> findById(String merchantId) {
        return merchantsRepository.findById(merchantId);
    }
}
