import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot")
  kotlin("plugin.spring")
}

dependencyCheck {
  suppressionFiles.add("dependency-check-suppress-json.xml")
}

dependencies {
  implementation("org.springframework.data:spring-data-elasticsearch:6.1.0")
  // Temporarily pin spring doc at 3.0.2 whilst waiting for 3.0.4 upgrade
  val springDocVersion = ":3.0.2"
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui$springDocVersion")
  implementation("org.springdoc:springdoc-openapi-starter-common$springDocVersion")
  constraints {
    implementation("org.webjars:swagger-ui:5.32.2")
  }
  implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
  implementation("org.opensearch.client:spring-data-opensearch-starter:3.0.6")
}

kotlin {
  jvmToolchain(25)
}

configure<com.gorylenko.GitPropertiesPluginExtension> {
  dotGitDirectory.set(File("${project.rootDir}/.git"))
}

tasks {
  bootJar { enabled = false }
  jar { enabled = true }
  copyAgent { enabled = false }

  withType<KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }
}
