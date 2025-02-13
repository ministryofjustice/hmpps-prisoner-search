@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot")
  kotlin("plugin.spring")
  kotlin("plugin.jpa") version "2.1.10"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencyCheck {
  suppressionFiles.add("dependency-check-suppress-json.xml")
}

testing {
  suites {
    register<JvmTestSuite>("testSmoke") {
      dependencies {
        implementation(project())
      }
    }
  }
}
configurations["testSmokeImplementation"].extendsFrom(configurations["testImplementation"])

dependencies {
  implementation("org.opensearch.client:spring-data-opensearch-starter:1.6.1")

  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.2.0")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-cache")

  implementation("org.springframework.data:spring-data-elasticsearch:5.4.2")

  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.4")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.3.0")
  // Leaving at version 2.9.0 to match App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L16
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.12.0")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  implementation("org.awaitility:awaitility-kotlin:4.2.2")

  implementation(project(":common"))

  runtimeOnly("org.postgresql:postgresql:42.7.5")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.2.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.25") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.28")
  testImplementation("org.wiremock:wiremock-standalone:3.12.0")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:4.1.0")
  testImplementation("com.google.code.gson:gson:2.12.1")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}
