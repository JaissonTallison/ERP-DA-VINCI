// Serviço implantável (um por bounded context, ADR-0005).
plugins {
    id("davinci.java-conventions")
    id("org.springframework.boot")
    // Com o plugin CycloneDX aplicado, o Spring Boot embute o SBOM no bootJar e o expõe em /actuator/sbom (ADR-0012).
    id("org.cyclonedx.bom")
}

springBoot {
    buildInfo()
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
