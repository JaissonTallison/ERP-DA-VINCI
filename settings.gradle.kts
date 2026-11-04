pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

plugins {
    // Baixa automaticamente o JDK 25 quando a máquina não tem um JDK completo (ADR-0006).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "erp-da-vinci"

// Bibliotecas técnicas compartilhadas (sem regra de negócio). Ver docs/architecture/MONOREPO.md.
include(":platform-kernel")
project(":platform-kernel").projectDir = file("libs/java/platform-kernel")

// Serviços de domínio: um por bounded context aprovado (docs/architecture/MICROSERVICES_CATALOG.md).
include(":tenant-registry")
project(":tenant-registry").projectDir = file("services/tenant-registry")
include(":identity")
project(":identity").projectDir = file("services/identity")
include(":organization")
project(":organization").projectDir = file("services/organization")
include(":governance")
project(":governance").projectDir = file("services/governance")
