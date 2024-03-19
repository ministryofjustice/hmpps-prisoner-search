import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot")
  kotlin("plugin.spring")
}

dependencyCheck {
  suppressionFiles.add("dependency-check-suppress-json.xml")
}

dependencies {
  implementation("org.springframework.data:spring-data-elasticsearch:5.2.4")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
}

kotlin {
  jvmToolchain(21)
}

tasks {
  bootJar { enabled = false }
  jar { enabled = true }
  copyAgent { enabled = false }

  withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = "21"
    }
  }
}
