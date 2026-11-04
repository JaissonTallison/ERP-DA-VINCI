plugins {
    id("davinci.spring-boot-service")
}

description = "Organization Management — grupos econômicos, entidades jurídicas e estrutura organizacional do cliente."

dependencies {
    implementation(project(":platform-kernel"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.opentelemetry)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.archunit.junit5)
}

// Critérios de aceite com tempo rodam sob demanda (./gradlew :organization:performanceTest), numa JVM só deles e com
// a máquina livre: no build comum, os contextos de outros testes e os testes de outros serviços distorcem a medição.
tasks.test {
    useJUnitPlatform { excludeTags("desempenho") }
}

val performanceTest by tasks.registering(Test::class) {
    description = "Testes de desempenho (critérios de aceite com tempo)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("desempenho") }
    shouldRunAfter(tasks.test)
}
