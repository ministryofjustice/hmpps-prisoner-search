plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.2.2"
  kotlin("plugin.spring") version "1.8.22"
}

dependencies {
  implementation("org.springframework.data:spring-data-elasticsearch:5.1.1")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(19))
}

tasks {
  bootJar { enabled = false }
  jar { enabled = true }
  copyAgent { enabled = false }

  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "19"
    }
  }
}
