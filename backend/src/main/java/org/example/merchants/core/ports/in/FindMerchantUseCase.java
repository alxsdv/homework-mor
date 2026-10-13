package org.example.merchants.core.ports.in;

import org.example.merchants.core.domain.Merchant;

import java.util.Optional;

public interface FindMerchantUseCase {

    /** Exact, case-sensitive match. */
    Optional<Merchant> findById(String merchantId);
}
