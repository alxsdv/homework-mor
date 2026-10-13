package org.example.merchants.core.ports.in;

import org.example.merchants.core.domain.MerchantBalance;

import java.util.List;

public interface GetMerchantBalancesUseCase {

    /**
     * One balance per currency the merchant has been paid in, ordered by currency; empty for a merchant without
     * payments. Throws MerchantNotFoundException for an unknown merchant (exact, case-sensitive match).
     */
    List<MerchantBalance> getBalances(String merchantId);
}
