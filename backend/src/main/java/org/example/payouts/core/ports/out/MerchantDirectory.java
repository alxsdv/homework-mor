package org.example.payouts.core.ports.out;

/** Payouts' view of the merchants it pays out to. */
public interface MerchantDirectory {

    /** Exact, case-sensitive match. */
    boolean exists(String merchantId);
}
