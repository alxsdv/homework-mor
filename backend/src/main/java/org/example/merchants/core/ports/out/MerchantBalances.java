package org.example.merchants.core.ports.out;

import org.example.merchants.core.domain.MerchantBalance;

import java.util.List;

public interface MerchantBalances {

    /** Ordered by currency; empty if the merchant has none. */
    List<MerchantBalance> balancesOf(String merchantId);
}
