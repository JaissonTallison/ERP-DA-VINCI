// Serviço implantável (um por bounded context, ADR-0005).
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("davinci.java-conventions")
    id("org.springframework.boot")
    // Com o plugin CycloneDX aplicado, o Spring Boot embute o SBOM no bootJar e o expõe em /actuator/sbom (ADR-0012).
    id("org.cyclonedx.bom")
}

val libs = the<LibrariesForLibs>()

springBoot {
    buildInfo()
}

dependencies {
    // Correções de segurança acima das versões do Spring Boot 4.1.2, apontadas pelo OSV-Scanner no SBOM.
    // Revisar a cada atualização do Boot e remover quando ele já trouxer versões corrigidas.
    implementation(platform(libs.opentelemetry.bom)) // GHSA-ccrj-gw8r-854m
    constraints {
        implementation(libs.snappy.java) {
            because("GHSA-gwq9-9ffj-mvrv, GHSA-q233-g92q-qxp6, GHSA-xqrx-qh46-34m7 (compressão do cliente Kafka)")
        }
        implementation(libs.lz4.java) {
            because("GHSA-mcr4-qmvw-px4g, GHSA-gm45-99xc-r7wv, GHSA-343h-94h5-c4wr (compressão do cliente Kafka)")
        }
    }
}

// Desenvolvimento: o bootRun usa o perfil `local` (valores do compose.yaml). Fora dele, tudo vem de variáveis.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("spring.profiles.active", "local")
}

tasks.named<Jar>("jar") {
    // Somente o bootJar é artefato de implantação.
    enabled = false
}

// Testes de contrato leem os documentos versionados em contracts/ pelo classpath (classpath:http/..., classpath:events/...).
sourceSets.named("test") {
    resources.srcDir(rootProject.layout.projectDirectory.dir("contracts"))
}

tasks.withType<Test>().configureEach {
    // Contexto Spring + Testcontainers precisa de mais heap que bibliotecas.
    maxHeapSize = "1g"
}
