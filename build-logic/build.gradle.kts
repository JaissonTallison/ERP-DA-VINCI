plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.cyclonedx.gradle.plugin)
    // Permite usar o catálogo `libs` dentro dos plugins de convenção.
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
