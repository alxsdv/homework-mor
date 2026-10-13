package org.example.merchants.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.merchants.core.ports.in.GetMerchantBalancesUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Errors are mapped to ProblemDetail responses by MerchantsExceptionHandler. */
@RestController
@Tag(name = "Merchants")
public class MerchantsController {

    private final GetMerchantBalancesUseCase getMerchantBalances;

    public MerchantsController(GetMerchantBalancesUseCase getMerchantBalances) {
        this.getMerchantBalances = getMerchantBalances;
    }

    /** What we owe the merchant, per currency. */
    @GetMapping(path = "/merchants/{merchantId}/balances", produces = "application/json")
    @Operation(summary = "What we owe the merchant, per currency")
    @ApiResponse(responseCode = "200", description = "The balances; empty for a merchant without payments")
    @ApiResponse(responseCode = "404", description = "No merchant with this ID")
    public MerchantBalancesResponse getBalances(
            @Parameter(example = "MERCH-001") @PathVariable("merchantId") String merchantId) {
        return MerchantBalancesResponse.fromDomain(merchantId, getMerchantBalances.getBalances(merchantId));
    }
}
