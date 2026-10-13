package org.example.payments;

import org.example.IntegrationTestBase;
import org.example.payments.core.ports.out.SettlementLedger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.jooq.Tables.PAYMENTS;
import static org.example.jooq.Tables.REFUNDS;
import static org.example.PaymentRequests.post;
import static org.example.PaymentRequests.postRefund;
import static org.example.PaymentRequests.recordPayment;
import static org.example.PaymentRequests.refund;
import static org.example.PaymentRequests.validPayment;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** Its own Spring context (because of @MockitoBean), and therefore its own Postgres container. */
class PaymentAtomicityIT extends IntegrationTestBase {

    @MockitoBean
    private SettlementLedger settlementLedger;

    @Test
    void ledgerFailureRollsBackThePayment() {
        doThrow(new IllegalStateException("simulated ledger failure"))
                .when(settlementLedger).recordSettlement(any());

        assertThat(post(http, validPayment()).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(dsl.fetchCount(PAYMENTS)).isZero();
    }

    @Test
    void ledgerFailureRollsBackTheRefund() {
        // The mocked ledger accepts the settlement and fails only on the refund.
        String paymentId = recordPayment(http, validPayment());
        doThrow(new IllegalStateException("simulated ledger failure"))
                .when(settlementLedger).recordRefund(any());

        assertThat(postRefund(http, paymentId, refund("RE-1", "2026-10-02T10:15:00+02:00", "59.50")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(dsl.fetchCount(REFUNDS)).isZero();
    }
}
