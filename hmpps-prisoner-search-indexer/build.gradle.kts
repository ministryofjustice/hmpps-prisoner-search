plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot")
  kotlin("plugin.spring")
  kotlin("plugin.jpa") version "1.9.10"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.opensearch.client:spring-data-opensearch-starter:1.2.0")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")

  implementation("org.springframework.data:spring-data-elasticsearch:5.1.4")

  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:2.1.0")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:1.30.0")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  implementation("org.awaitility:awaitility-kotlin:4.2.0")

  implementation(project(":common"))

  runtimeOnly("org.postgresql:postgresql:42.6.0")
  runtimeOnly("org.flywaydb:flyway-core")

  testImplementation("io.jsonwebtoken:jjwt-impl:0.12.0")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.12.0")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.16")
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
  testImplementation("org.wiremock:wiremock:3.2.0")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:3.2.2")
  testImplementation("com.google.code.gson:gson:2.10.1")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(20))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "20"
    }
  }

  test {
    // required for jjwt 0.12 - see https://github.com/jwtk/jjwt/issues/849
    jvmArgs("--add-exports", "java.base/sun.security.util=ALL-UNNAMED")
  }
}
