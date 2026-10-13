package org.example.merchants.rest;

import org.example.merchants.core.ports.in.GetMerchantBalancesUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Errors are mapped to ProblemDetail responses by MerchantsExceptionHandler. */
@RestController
public class MerchantsController {

    private final GetMerchantBalancesUseCase getMerchantBalances;

    public MerchantsController(GetMerchantBalancesUseCase getMerchantBalances) {
        this.getMerchantBalances = getMerchantBalances;
    }

    /** What we owe the merchant, per currency. */
    @GetMapping(path = "/merchants/{merchantId}/balances", produces = "application/json")
    public MerchantBalancesResponse getBalances(@PathVariable("merchantId") String merchantId) {
        return MerchantBalancesResponse.fromDomain(merchantId, getMerchantBalances.getBalances(merchantId));
    }
}
