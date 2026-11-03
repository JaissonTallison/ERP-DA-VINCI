plugins {
    id("davinci.spring-boot-service")
}

description = "Identity — usuários e organizações no IdP regional; sincroniza tenants do Tenant Registry com o Keycloak."

dependencies {
    implementation(project(":platform-kernel"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.opentelemetry)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.json.schema.validator)
}

// O teste de integração sobe o Keycloak com o mesmo realm do ambiente local (classpath:davinci-realm.json).
sourceSets.named("test") {
    resources.srcDir(rootProject.layout.projectDirectory.dir("infra/local/keycloak"))
}
