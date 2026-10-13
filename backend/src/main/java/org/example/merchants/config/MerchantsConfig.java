package org.example.merchants.config;

import org.example.merchants.core.ports.in.FindMerchantUseCase;
import org.example.merchants.core.ports.in.GetMerchantBalancesUseCase;
import org.example.merchants.core.ports.out.MerchantBalances;
import org.example.merchants.core.ports.out.MerchantsRepository;
import org.example.merchants.core.services.FindMerchantUseCaseImpl;
import org.example.merchants.core.services.GetMerchantBalancesUseCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MerchantsConfig {

    @Bean
    FindMerchantUseCase findMerchantUseCase(MerchantsRepository merchantsRepository) {
        return new FindMerchantUseCaseImpl(merchantsRepository);
    }

    @Bean
    GetMerchantBalancesUseCase getMerchantBalancesUseCase(MerchantsRepository merchantsRepository,
                                                          MerchantBalances merchantBalances) {
        return new GetMerchantBalancesUseCaseImpl(merchantsRepository, merchantBalances);
    }
}
