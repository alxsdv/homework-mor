package org.example.merchants.core.services;

import org.example.merchants.core.domain.Merchant;
import org.example.merchants.core.domain.MerchantBalance;
import org.example.merchants.core.ports.in.MerchantNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetMerchantBalancesUseCaseImplTest {

    private static final MerchantBalance EUR_194 =
            new MerchantBalance(Currency.getInstance("EUR"), new BigDecimal("194.00"));

    private final Set<String> knownMerchants = Set.of("MERCH-001", "MERCH-003");
    private final Map<String, List<MerchantBalance>> ledger = Map.of("MERCH-001", List.of(EUR_194));
    private final List<String> balanceQueries = new ArrayList<>();
    private final GetMerchantBalancesUseCaseImpl useCase = new GetMerchantBalancesUseCaseImpl(
            merchantId -> knownMerchants.contains(merchantId)
                    ? Optional.of(new Merchant(merchantId, new BigDecimal("0.03")))
                    : Optional.empty(),
            merchantId -> {
                balanceQueries.add(merchantId);
                return ledger.getOrDefault(merchantId, List.of());
            });

    @Test
    void returnsTheMerchantsBalances() {
        assertThat(useCase.getBalances("MERCH-001")).containsExactly(EUR_194);
    }

    @Test
    void knownMerchantWithoutPaymentsHasNoBalances() {
        assertThat(useCase.getBalances("MERCH-003")).isEmpty();
    }

    @Test
    void unknownMerchantIsNotFoundAndTheLedgerIsNotAsked() {
        assertThatThrownBy(() -> useCase.getBalances("MERCH-999"))
                .isInstanceOf(MerchantNotFoundException.class)
                .hasMessageContaining("MERCH-999");
        assertThat(balanceQueries).isEmpty();
    }
}
