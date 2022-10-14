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
    `java-library`
    id("io.gitlab.arturbosch.detekt") version "1.17.1"
    id("pl.allegro.tech.build.axion-release") version "1.14.0"
    id("com.gradle.plugin-publish") version "0.21.0"
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

scmVersion {
    versionCreator("versionWithBranch")
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


publishing {
    publications {
        create<MavenPublication>("sonatype") {
            artifactId = "couchbase"
            from(components.findByName("java"))
            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }
            pom {
                name.set("couchbase-commons")
                url.set("https://github.com/allegro/couchbase-commons")
                inceptionYear.set("2022")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("allegro")
                        name.set("opensource@allegro.pl")
                    }
                }
                scm {
                    connection.set("scm:git@github.com:allegro/couchbase-commons.git")
                    developerConnection.set("scm:git@github.com:allegro/couchbase-commons.git")
                    url.set("https://github.com/allegro/couchbase-commons")
                }
            }
        }
    }
    repositories {
        maven {
            val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        }
    }
}