plugins {
    id("com.coditory.integration-test") version "1.4.4"
    `idea`
}

dependencies {
    implementation(platform(kotlin("bom")))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("io.micrometer:micrometer-core:1.9.5")
    api(group = "com.couchbase.client", name = "java-client", version = "3.2.5")
    api(group = "com.couchbase.client", name = "metrics-micrometer", version = "0.1.0")
    api(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = "2.13.4")

    testImplementation(group = "io.kotest", name = "kotest-runner-junit5", version = "5.1.0")
    testImplementation(group = "io.kotest.extensions", name = "kotest-extensions-testcontainers", version = "1.3.4")
    testImplementation(group = "org.testcontainers", name = "couchbase", version = "1.17.5")
    testImplementation(group = "org.mockito.kotlin", name = "mockito-kotlin", version = "4.0.0")
}
