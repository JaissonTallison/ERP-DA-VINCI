// Serviço implantável (um por bounded context). Primeiro uso previsto na Fase 2.
plugins {
    id("davinci.java-conventions")
    id("org.springframework.boot")
}

springBoot {
    buildInfo()
}

tasks.named<Jar>("jar") {
    // Somente o bootJar é artefato de implantação.
    enabled = false
}
