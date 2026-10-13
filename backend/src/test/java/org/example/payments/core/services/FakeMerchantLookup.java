package org.example.payments.core.services;

import org.example.payments.core.domain.Merchant;
import org.example.payments.core.ports.out.MerchantLookup;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

class FakeMerchantLookup implements MerchantLookup {

    private final Map<String, BigDecimal> feeRates;

    FakeMerchantLookup(Map<String, BigDecimal> feeRates) {
        this.feeRates = feeRates;
    }

    @Override
    public Optional<Merchant> findById(String merchantId) {
        return Optional.ofNullable(feeRates.get(merchantId)).map(rate -> new Merchant(merchantId, rate));
    }
}
