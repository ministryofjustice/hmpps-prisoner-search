import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot")
  kotlin("plugin.spring")
}

dependencyCheck {
  suppressionFiles.add("dependency-check-suppress-json.xml")
}

dependencies {
  implementation("org.springframework.data:spring-data-elasticsearch:5.4.4")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.opensearch.client:spring-data-opensearch-starter:1.6.2")
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    freeCompilerArgs.add("-Xwhen-guards")
  }
}

configure<com.gorylenko.GitPropertiesPluginExtension> {
  dotGitDirectory.set(File("${project.rootDir}/.git"))
}

tasks {
  bootJar { enabled = false }
  jar { enabled = true }
  copyAgent { enabled = false }

  withType<KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}
