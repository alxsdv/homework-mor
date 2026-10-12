package org.example;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Module boundaries between payments, ledger and shared. */
@AnalyzeClasses(packages = "org.example", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule paymentsCoreDoesNotKnowTheLedger = noClasses()
            .that().resideInAPackage("org.example.payments.core..")
            .should().dependOnClassesThat().resideInAPackage("org.example.ledger..");

    @ArchTest
    static final ArchRule onlyPaymentsOutboundAdaptersUseTheLedger = noClasses()
            .that().resideInAPackage("org.example.payments..")
            .and().resideOutsideOfPackage("org.example.payments.adapters.out..")
            .should().dependOnClassesThat().resideInAPackage("org.example.ledger..");

    @ArchTest
    static final ArchRule paymentsUsesOnlyTheLedgersPublicApi = noClasses()
            .that().resideInAPackage("org.example.payments..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.example.ledger.core.ports.out..",
                    "org.example.ledger.core.services..",
                    "org.example.ledger.adapters..",
                    "org.example.ledger.config..");

    @ArchTest
    static final ArchRule paymentsDoesNotTouchLedgerTables = noClasses()
            .that().resideInAPackage("org.example.payments..")
            .should().dependOnClassesThat(
                    resideInAPackage("org.example.jooq..").and(simpleNameStartingWith("Ledger")));

    @ArchTest
    static final ArchRule ledgerDoesNotKnowPayments = noClasses()
            .that().resideInAPackage("org.example.ledger..")
            .should().dependOnClassesThat().resideInAPackage("org.example.payments..");

    @ArchTest
    static final ArchRule ledgerDoesNotTouchPaymentTables = noClasses()
            .that().resideInAPackage("org.example.ledger..")
            .should().dependOnClassesThat(
                    resideInAPackage("org.example.jooq..")
                            .and(simpleNameStartingWith("Payments").or(simpleNameStartingWith("Merchants"))));

    @ArchTest
    static final ArchRule sharedDependsOnNeitherModule = noClasses()
            .that().resideInAPackage("org.example.shared..")
            .should().dependOnClassesThat().resideInAnyPackage("org.example.payments..", "org.example.ledger..");
}
