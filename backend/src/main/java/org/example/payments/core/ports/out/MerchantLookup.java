package org.example.payments.core.ports.out;

import org.example.payments.core.domain.Merchant;

import java.util.Optional;

/** Payments' view of the merchants it settles for. */
public interface MerchantLookup {

    /** Exact, case-sensitive match. */
    Optional<Merchant> findById(String merchantId);
}
