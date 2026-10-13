package org.example;

import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Module boundaries. payments → merchants → ledger, and payments → ledger. A module calls another only from its
 * adapters.out, only through the other's core.ports.in and core.domain, and touches only its own jOOQ tables.
 */
@AnalyzeClasses(packages = "org.example", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static ArchRule onlyOutboundAdaptersUse(String caller, String... callees) {
        String[] calleePackages = new String[callees.length];
        for (int i = 0; i < callees.length; i++) {
            calleePackages[i] = "org.example." + callees[i] + "..";
        }
        return noClasses()
                .that().resideInAPackage("org.example." + caller + "..")
                .and().resideOutsideOfPackage("org.example." + caller + ".adapters.out..")
                .should().dependOnClassesThat().resideInAnyPackage(calleePackages);
    }

    private static ArchRule usesOnlyThePublicApiOf(String caller, String callee) {
        String internals = "org.example." + callee;
        return noClasses()
                .that().resideInAPackage("org.example." + caller + "..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        internals + ".core.ports.out..",
                        internals + ".core.services..",
                        internals + ".adapters..",
                        internals + ".config..",
                        internals + ".rest..");
    }

    /**
     * A reference to {@code Tables.LEDGER_ACCOUNTS} is recorded as a dependency on {@code Tables}, not on the table
     * class, so the rule also checks the type of every jOOQ field the module reads.
     */
    private static ArchRule touchesOnlyItsOwnTables(String module, String... foreignTablePrefixes) {
        var foreign = simpleNameStartingWith(foreignTablePrefixes[0]);
        for (int i = 1; i < foreignTablePrefixes.length; i++) {
            foreign = foreign.or(simpleNameStartingWith(foreignTablePrefixes[i]));
        }
        var foreignTable = resideInAPackage("org.example.jooq..").and(foreign);
        return noClasses()
                .that().resideInAPackage("org.example." + module + "..")
                .should().dependOnClassesThat(foreignTable)
                .orShould().accessFieldWhere(describe(
                        "the field's type is " + foreignTable.getDescription(),
                        (JavaFieldAccess access) -> foreignTable.test(access.getTarget().getRawType())));
    }

    @ArchTest
    static final ArchRule onlyPaymentsOutboundAdaptersUseOtherModules =
            onlyOutboundAdaptersUse("payments", "merchants", "ledger");

    @ArchTest
    static final ArchRule onlyMerchantsOutboundAdaptersUseTheLedger =
            onlyOutboundAdaptersUse("merchants", "ledger");

    @ArchTest
    static final ArchRule paymentsUsesOnlyMerchantsPublicApi = usesOnlyThePublicApiOf("payments", "merchants");

    @ArchTest
    static final ArchRule paymentsUsesOnlyLedgersPublicApi = usesOnlyThePublicApiOf("payments", "ledger");

    @ArchTest
    static final ArchRule merchantsUsesOnlyLedgersPublicApi = usesOnlyThePublicApiOf("merchants", "ledger");

    @ArchTest
    static final ArchRule ledgerKnowsNoOtherModule = noClasses()
            .that().resideInAPackage("org.example.ledger..")
            .should().dependOnClassesThat().resideInAnyPackage("org.example.payments..", "org.example.merchants..");

    @ArchTest
    static final ArchRule merchantsDoesNotKnowPayments = noClasses()
            .that().resideInAPackage("org.example.merchants..")
            .should().dependOnClassesThat().resideInAPackage("org.example.payments..");

    @ArchTest
    static final ArchRule paymentsTouchesOnlyPaymentTables =
            touchesOnlyItsOwnTables("payments", "Ledger", "Merchants");

    @ArchTest
    static final ArchRule merchantsTouchesOnlyMerchantTables =
            touchesOnlyItsOwnTables("merchants", "Ledger", "Payments");

    @ArchTest
    static final ArchRule ledgerTouchesOnlyLedgerTables =
            touchesOnlyItsOwnTables("ledger", "Payments", "Merchants");

    @ArchTest
    static final ArchRule sharedDependsOnNoModule = noClasses()
            .that().resideInAPackage("org.example.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.example.payments..", "org.example.merchants..", "org.example.ledger..");

    /** Covers shared and jooq as well; neither depends on a module, so this is the stricter check. */
    @ArchTest
    static final ArchRule packagesAreFreeOfCycles = slices().matching("org.example.(*)..").should().beFreeOfCycles();
}
