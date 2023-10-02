plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot")
  kotlin("plugin.spring")
}

dependencies {
  implementation("org.springframework.data:spring-data-elasticsearch:5.1.4")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(20))
}

tasks {
  bootJar { enabled = false }
  jar { enabled = true }
  copyAgent { enabled = false }

  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "20"
    }
  }
}
