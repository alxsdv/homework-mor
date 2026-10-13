package org.example.merchants.core.ports.out;

import org.example.merchants.core.domain.Merchant;

import java.util.Optional;

public interface MerchantsRepository {

    /** Exact, case-sensitive match. */
    Optional<Merchant> findById(String merchantId);
}
