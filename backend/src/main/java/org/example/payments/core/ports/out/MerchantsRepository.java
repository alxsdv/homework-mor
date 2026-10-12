package org.example.payments.core.ports.out;

import org.example.payments.core.domain.Merchant;

import java.util.Optional;

public interface MerchantsRepository {

    /** Exact, case-sensitive match. */
    Optional<Merchant> findById(String merchantId);
}
