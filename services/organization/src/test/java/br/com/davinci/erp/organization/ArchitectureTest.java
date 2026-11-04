package br.com.davinci.erp.organization;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Direção de dependência entre módulos e pureza do domínio (ADR-0018). */
@AnalyzeClasses(packages = "br.com.davinci.erp.organization", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule dominioSemFramework = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "tools.jackson..", "java.sql..", "jakarta..")
            .because("o domínio é Java puro e testável sem infraestrutura");

    @ArchTest
    static final ArchRule compartilhadoNaoConheceModulos = noClasses()
            .that()
            .resideInAPackage("..organization.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..organization.corporate..", "..organization.structure..", "..organization.costing..");

    @ArchTest
    static final ArchRule corporativoNaoConheceEstruturaNemCustos = noClasses()
            .that()
            .resideInAPackage("..organization.corporate..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..organization.structure..", "..organization.costing..");

    @ArchTest
    static final ArchRule dominioNaoConheceCamadasDeFora = noClasses()
            .that()
            .resideInAPackage("..corporate.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..corporate.application..", "..corporate.persistence..", "..corporate.api..");
}
