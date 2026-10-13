package org.example.payments.adapters.out;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantLookupAdapterTest {

    private final MerchantLookupAdapter adapter = new MerchantLookupAdapter(merchantId ->
            merchantId.equals("MERCH-001")
                    ? Optional.of(new org.example.merchants.core.domain.Merchant("MERCH-001", new BigDecimal("0.03")))
                    : Optional.empty());

    @Test
    void copiesIdAndFeeRate() {
        assertThat(adapter.findById("MERCH-001")).hasValueSatisfying(merchant -> {
            assertThat(merchant.id()).isEqualTo("MERCH-001");
            assertThat(merchant.feeRate()).isEqualByComparingTo("0.03");
        });
    }

    @Test
    void unknownMerchantStaysEmpty() {
        assertThat(adapter.findById("MERCH-999")).isEmpty();
    }
}
