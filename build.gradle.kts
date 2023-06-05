plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.2.0"
  kotlin("plugin.spring") version "1.8.21"
}

configurations {
  implementation { exclude(module = "spring-boot-starter-web") }
  implementation { exclude(module = "spring-boot-starter-tomcat") }
  testImplementation { exclude(group = "org.junit.vintage") }
}

// temporarily keep this at 5.0.5 as 5.1.0 not compatible with opensearch
val springDataElasticsearchVersion by extra("5.0.5")

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.opensearch.client:spring-data-opensearch-starter:1.1.0")

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  implementation("org.springframework.data:spring-data-elasticsearch:$springDataElasticsearchVersion")

  implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:2.0.0")

  // TODO: work out whether can just use jackson instead
  implementation("com.google.code.gson:gson:2.10.1")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  implementation("io.arrow-kt:arrow-core:1.1.5")
  implementation("org.awaitility:awaitility-kotlin:4.2.0")

  testImplementation("io.jsonwebtoken:jjwt-impl:0.11.5")
  testImplementation("io.jsonwebtoken:jjwt-jackson:0.11.5")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.15")
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
  testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:2.35.0")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.38.0")
  testImplementation("io.kotest.extensions:kotest-assertions-arrow:1.3.3")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(19))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "19"
    }
  }
}
