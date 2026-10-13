package org.example.payments.core.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundIdTest {

    @Test
    void acceptsGeneratedFormat() {
        assertThat(new RefundId("REF-7K2M9QX4TB").value()).isEqualTo("REF-7K2M9QX4TB");
    }

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(strings = {"PAY-7K2M9QX4TB", "ref-7K2M9QX4TB", "REF-7k2m9qx4tb", "REF-7K2M9QX4T", "REF-7K2M9QX4TBI"})
    void rejectsOtherFormats(String value) {
        assertThatThrownBy(() -> new RefundId(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(value);
    }
}
