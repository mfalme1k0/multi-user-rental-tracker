package com.rentaltracker.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The layering from docs/architecture.md as failing tests. A PR that breaks a rule cannot be merged green.
 *
 * <pre>
 *   transport -> service -> repository (interfaces) <- repository.sqlite -> infrastructure
 *   domain and exception are shared by everyone and depend on nothing above them
 * </pre>
 */
@AnalyzeClasses(packages = "com.rentaltracker", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String TRANSPORT = "..rentaltracker.transport..";
    private static final String SERVICE = "..rentaltracker.service..";
    private static final String REPOSITORY = "..rentaltracker.repository..";
    private static final String SQLITE = "..rentaltracker.repository.sqlite..";
    private static final String INFRASTRUCTURE = "..rentaltracker.infrastructure..";
    private static final String DOMAIN = "..rentaltracker.domain..";
    private static final String EXCEPTION = "..rentaltracker.exception..";

    @ArchTest
    static final ArchRule transportTalksOnlyToService =
            noClasses().that().resideInAPackage(TRANSPORT)
                    .should().dependOnClassesThat().resideInAnyPackage(REPOSITORY, INFRASTRUCTURE);

    @ArchTest
    static final ArchRule serviceNeverTouchesSqlOrUi =
            noClasses().that().resideInAPackage(SERVICE)
                    .should().dependOnClassesThat().resideInAnyPackage(SQLITE, INFRASTRUCTURE, TRANSPORT);

    @ArchTest
    static final ArchRule repositoryInterfacesDependOnNothingConcrete =
            noClasses().that().resideInAPackage("com.rentaltracker.repository")
                    .should().dependOnClassesThat().resideInAnyPackage(SQLITE, INFRASTRUCTURE, SERVICE, TRANSPORT);

    @ArchTest
    static final ArchRule sqliteImplementationsDoNotReachUpwards =
            noClasses().that().resideInAPackage(SQLITE)
                    .should().dependOnClassesThat().resideInAnyPackage(SERVICE, TRANSPORT);

    @ArchTest
    static final ArchRule infrastructureDoesNotReachUpwards =
            noClasses().that().resideInAPackage(INFRASTRUCTURE)
                    .should().dependOnClassesThat().resideInAnyPackage(SERVICE, TRANSPORT, REPOSITORY);

    @ArchTest
    static final ArchRule domainIsPure =
            noClasses().that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage(TRANSPORT, SERVICE, REPOSITORY, INFRASTRUCTURE);

    @ArchTest
    static final ArchRule exceptionsDependOnNothing =
            noClasses().that().resideInAPackage(EXCEPTION)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            TRANSPORT, SERVICE, REPOSITORY, INFRASTRUCTURE, DOMAIN);

    @ArchTest
    static final ArchRule onlyPersistenceCodeMayUseJdbc =
            noClasses().that().resideOutsideOfPackages(SQLITE, INFRASTRUCTURE)
                    .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "org.sqlite..");

    @ArchTest
    static final ArchRule onlyTransportMayUseConsole =
            noClasses().that().resideOutsideOfPackages(TRANSPORT)
                    .and().doNotHaveFullyQualifiedName("com.rentaltracker.Main")
                    .should().accessField(System.class, "out")
                    .orShould().accessField(System.class, "in")
                    .orShould().accessField(System.class, "err");
}
