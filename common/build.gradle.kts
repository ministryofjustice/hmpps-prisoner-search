import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot")
  kotlin("plugin.spring")
}

dependencyCheck {
  suppressionFiles.add("dependency-check-suppress-json.xml")
}

dependencies {
  implementation("org.springframework.data:spring-data-elasticsearch:5.5.4")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.opensearch.client:spring-data-opensearch-starter:2.0.2")
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjvm-default=all", "-Xwhen-guards", "-Xannotation-default-target=param-property")
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_24
  targetCompatibility = JavaVersion.VERSION_24
}

configure<com.gorylenko.GitPropertiesPluginExtension> {
  dotGitDirectory.set(File("${project.rootDir}/.git"))
}

tasks {
  bootJar { enabled = false }
  jar { enabled = true }
  copyAgent { enabled = false }

  withType<KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24
  }
}
