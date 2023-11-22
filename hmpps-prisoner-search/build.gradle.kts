import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot")
  kotlin("plugin.spring")
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.opensearch.client:spring-data-opensearch-starter:1.2.1")
  implementation("com.google.code.gson:gson:2.10.1")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-cache")

  implementation("org.springframework.data:spring-data-elasticsearch:5.1.5")

  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")

  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:1.31.0")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  implementation("io.arrow-kt:arrow-core:1.2.1")
  implementation("org.awaitility:awaitility-kotlin:4.2.0")

  implementation(project(":common"))

  testImplementation("org.springframework.boot:spring-boot-starter-webflux")
  testImplementation("io.jsonwebtoken:jjwt-impl:0.12.3")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.12.3")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.19") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.19")
  testImplementation("org.wiremock:wiremock:3.3.1")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:3.2.2")
  testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.4.0")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = "21"
    }
  }
}
