import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21")
        classpath("org.jetbrains.kotlin:kotlin-allopen:1.6.21")
    }
}

plugins {
    id("io.gitlab.arturbosch.detekt") version "1.17.1"
    id("pl.allegro.tech.build.axion-release") version "1.14.0"
}

scmVersion {
    tag {
        prefix.set("")
    }
}

allprojects {
    apply(plugin = "pl.allegro.tech.build.axion-release")
    project.group = "pl.allegro.tech.couchbase-commons"
    project.version = scmVersion.version

    repositories {
        mavenCentral()
    }

    apply(plugin = "kotlin")

}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = freeCompilerArgs + "-Xinline-classes"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    detekt {
        input = files("src/main/kotlin", "src/test/kotlin", "src/integration/kotlin")
        config = files("$rootDir/config/detekt/default-detekt-config.yml", "$rootDir/config/detekt/detekt-config.yml")
    }
}
