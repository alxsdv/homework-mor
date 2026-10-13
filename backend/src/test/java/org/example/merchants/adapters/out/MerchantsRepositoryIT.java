package org.example.merchants.adapters.out;

import org.example.IntegrationTestBase;
import org.example.merchants.core.ports.out.MerchantsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantsRepositoryIT extends IntegrationTestBase {

    @Autowired
    private MerchantsRepository merchantsRepository;

    @Test
    void findsSeededMerchantWithFeeRate() {
        assertThat(merchantsRepository.findById("MERCH-001"))
                .hasValueSatisfying(merchant -> assertThat(merchant.feeRate()).isEqualByComparingTo("0.03"));
    }

    @Test
    void unknownMerchantIsEmpty() {
        assertThat(merchantsRepository.findById("MERCH-999")).isEmpty();
    }

    @Test
    void matchesIdExactly() {
        assertThat(merchantsRepository.findById("merch-001")).isEmpty();
    }
}
