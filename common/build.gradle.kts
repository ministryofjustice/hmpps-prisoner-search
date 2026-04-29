import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot")
  kotlin("plugin.spring")
}

dependencyCheck {
  suppressionFiles.add("dependency-check-suppress-json.xml")
}

dependencies {
  implementation("org.springframework.data:spring-data-elasticsearch:6.0.5")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
  implementation("org.springdoc:springdoc-openapi-starter-common:3.0.2")
  constraints {
    implementation("org.webjars:swagger-ui:5.32.2")
  }
  implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
  implementation("org.opensearch.client:spring-data-opensearch-starter:3.0.5")

  val appinsightsCore = "core:2.6.4"
  implementation("io.micrometer:micrometer-registry-azure-monitor:1.16.5")
  implementation("com.microsoft.azure:applicationinsights-$appinsightsCore")
}

kotlin {
  jvmToolchain(25)
  compilerOptions {
    freeCompilerArgs.addAll("-Xannotation-default-target=param-property")
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
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }
}
