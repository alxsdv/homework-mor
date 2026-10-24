package org.example.ledger.core.domain;

import org.jspecify.annotations.Nullable;

import java.util.Currency;

/**
 * Identifies a ledger account. merchantId is set only for the per-merchant types, countryCode only for TAX_PAYABLE.
 * Mirrors the ledger_accounts_key_parts CHECK constraint.
 */
public record AccountKey(AccountType type, Currency currency, @Nullable String merchantId,
                         @Nullable String countryCode) {

    public AccountKey {
        boolean needsMerchant = type.isPerMerchant();
        boolean needsCountry = type == AccountType.TAX_PAYABLE;
        if (needsMerchant && merchantId == null) {
            throw new IllegalArgumentException(type + " account requires a merchantId");
        }
        if (!needsMerchant && merchantId != null) {
            throw new IllegalArgumentException(type + " account must not have a merchantId");
        }
        if (needsCountry && countryCode == null) {
            throw new IllegalArgumentException(type + " account requires a countryCode");
        }
        if (!needsCountry && countryCode != null) {
            throw new IllegalArgumentException(type + " account must not have a countryCode");
        }
    }

    public static AccountKey cashClearing(Currency currency) {
        return new AccountKey(AccountType.CASH_CLEARING, currency, null, null);
    }

    public static AccountKey feeRevenue(Currency currency) {
        return new AccountKey(AccountType.FEE_REVENUE, currency, null, null);
    }

    public static AccountKey merchantPayable(Currency currency, String merchantId) {
        return new AccountKey(AccountType.MERCHANT_PAYABLE, currency, merchantId, null);
    }

    public static AccountKey merchantPayoutPending(Currency currency, String merchantId) {
        return new AccountKey(AccountType.MERCHANT_PAYOUT_PENDING, currency, merchantId, null);
    }

    public static AccountKey taxPayable(Currency currency, String countryCode) {
        return new AccountKey(AccountType.TAX_PAYABLE, currency, null, countryCode);
    }
}
