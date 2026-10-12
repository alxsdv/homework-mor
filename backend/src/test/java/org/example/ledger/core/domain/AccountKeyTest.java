package org.example.ledger.core.domain;

import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountKeyTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void factoriesSetOnlyTheKeyPartsTheirTypeUses() {
        assertThat(AccountKey.cashClearing(EUR)).isEqualTo(new AccountKey(AccountType.CASH_CLEARING, EUR, null, null));
        assertThat(AccountKey.feeRevenue(EUR)).isEqualTo(new AccountKey(AccountType.FEE_REVENUE, EUR, null, null));
        assertThat(AccountKey.merchantPayable(EUR, "MERCH-001"))
                .isEqualTo(new AccountKey(AccountType.MERCHANT_PAYABLE, EUR, "MERCH-001", null));
        assertThat(AccountKey.taxPayable(EUR, "DE"))
                .isEqualTo(new AccountKey(AccountType.TAX_PAYABLE, EUR, null, "DE"));
    }

    @Test
    void rejectsKeyPartsThatDoNotApply() {
        assertThatThrownBy(() -> new AccountKey(AccountType.CASH_CLEARING, EUR, "MERCH-001", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountKey(AccountType.FEE_REVENUE, EUR, null, "DE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountKey(AccountType.MERCHANT_PAYABLE, EUR, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountKey(AccountType.MERCHANT_PAYABLE, EUR, "MERCH-001", "DE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountKey(AccountType.TAX_PAYABLE, EUR, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyMerchantAccountsCacheTheirBalance() {
        assertThat(AccountType.MERCHANT_PAYABLE.hasCachedBalance()).isTrue();
        assertThat(AccountType.CASH_CLEARING.hasCachedBalance()).isFalse();
        assertThat(AccountType.FEE_REVENUE.hasCachedBalance()).isFalse();
        assertThat(AccountType.TAX_PAYABLE.hasCachedBalance()).isFalse();
    }
}
