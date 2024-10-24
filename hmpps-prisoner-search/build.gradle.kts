@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot")
  kotlin("plugin.spring")
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
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.0.8")
  implementation("org.opensearch.client:spring-data-opensearch-starter:1.5.3")
  implementation("com.google.code.gson:gson:2.11.0")
  implementation("org.springframework.boot:spring-boot-starter-cache")

  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.data:spring-data-elasticsearch:5.3.5")

  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.9.0")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  implementation("org.awaitility:awaitility-kotlin:4.2.2")

  implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

  implementation(project(":common"))

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.0.8")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.23") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.25")
  testImplementation("org.wiremock:wiremock-standalone:3.9.2")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:3.4.1")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}
