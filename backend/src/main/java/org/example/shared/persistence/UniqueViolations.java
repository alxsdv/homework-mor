package org.example.shared.persistence;

import org.jspecify.annotations.Nullable;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/** Reads the violated constraint's name from a Postgres error anywhere in an exception's cause chain. */
public final class UniqueViolations {

    private UniqueViolations() {}

    public static @Nullable String constraintName(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof PSQLException postgresError) {
                ServerErrorMessage serverError = postgresError.getServerErrorMessage();
                return serverError == null ? null : serverError.getConstraint();
            }
            current = current.getCause();
        }
        return null;
    }
}
