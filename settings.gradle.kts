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

// Bibliotecas técnicas compartilhadas (sem regra de negócio). Ver docs/architecture/monorepo.md.
include(":platform-kernel")
project(":platform-kernel").projectDir = file("libs/java/platform-kernel")

// Serviços de domínio entram aqui a partir da Fase 2, um por bounded context aprovado.
