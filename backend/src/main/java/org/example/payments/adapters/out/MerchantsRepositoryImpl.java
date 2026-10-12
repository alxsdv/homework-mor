package org.example.payments.adapters.out;

import org.example.payments.core.domain.Merchant;
import org.example.payments.core.ports.out.MerchantsRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static org.example.jooq.Tables.MERCHANTS;

@Repository
public class MerchantsRepositoryImpl implements MerchantsRepository {

    private final DSLContext dsl;

    public MerchantsRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<Merchant> findById(String merchantId) {
        return dsl.selectFrom(MERCHANTS)
                .where(MERCHANTS.ID.eq(merchantId))
                .fetchOptional()
                .map(record -> new Merchant(record.getId(), record.getFeeRate()));
    }
}
