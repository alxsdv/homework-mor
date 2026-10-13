package org.example.merchants.config;

import org.example.merchants.core.ports.in.FindMerchantUseCase;
import org.example.merchants.core.ports.out.MerchantsRepository;
import org.example.merchants.core.services.FindMerchantUseCaseImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MerchantsConfig {

    @Bean
    FindMerchantUseCase findMerchantUseCase(MerchantsRepository merchantsRepository) {
        return new FindMerchantUseCaseImpl(merchantsRepository);
    }
}
