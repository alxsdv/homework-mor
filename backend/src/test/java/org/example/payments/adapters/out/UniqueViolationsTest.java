package org.example.payments.adapters.out;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueViolationsTest {

    @Test
    void readsConstraintNameFromPostgresErrorInCauseChain() {
        // Postgres error fields are NUL-separated, each prefixed with a type code; 'n' is the constraint name.
        PSQLException postgresError = new PSQLException(new ServerErrorMessage(
                "SERROR\0C23505\0Mduplicate key value violates unique constraint\0npayments_public_id_key\0"));
        DuplicateKeyException exception = new DuplicateKeyException("jOOQ; duplicate key", postgresError);

        assertThat(UniqueViolations.constraintName(exception)).isEqualTo("payments_public_id_key");
    }

    @Test
    void returnsNullWithoutPostgresCause() {
        assertThat(UniqueViolations.constraintName(new DuplicateKeyException("no cause"))).isNull();
    }
}
