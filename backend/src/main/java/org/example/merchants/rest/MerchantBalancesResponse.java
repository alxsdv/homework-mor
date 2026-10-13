package org.example.merchants.rest;

import org.example.merchants.core.domain.MerchantBalance;

import java.util.List;

/** 200 body for GET /merchants/{merchantId}/balances: amounts as strings at the currency's scale. */
public record MerchantBalancesResponse(String merchantId, List<Balance> balances) {

    public record Balance(String currency, String payable) {}

    public static MerchantBalancesResponse fromDomain(String merchantId, List<MerchantBalance> balances) {
        return new MerchantBalancesResponse(merchantId, balances.stream()
                .map(balance -> new Balance(balance.currency().getCurrencyCode(), balance.payable().toPlainString()))
                .toList());
    }
}
