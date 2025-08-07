@file:Suppress("UnstableApiUsage")

import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot")
  id("org.openapi.generator") version "7.14.0"
  kotlin("plugin.spring")
  kotlin("plugin.jpa") version "2.2.0"
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencyCheck {
  suppressionFiles.add("dependency-check-suppress-json.xml")
}

testing {
  suites {
    register<JvmTestSuite>("testSmoke") {
      dependencies {
        implementation(project())
      }
    }
  }
}
configurations["testSmokeImplementation"].extendsFrom(configurations["testImplementation"])

dependencies {
  implementation("org.opensearch.client:spring-data-opensearch-starter:2.0.0")

  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:1.5.0-beta")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-cache")

  implementation("org.springframework.data:spring-data-elasticsearch:5.5.2")

  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:5.4.10")
  // Leaving at version 2.9.0 to match App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L16
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.16.0")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  implementation("org.awaitility:awaitility-kotlin:4.3.0")
  implementation("com.github.ben-manes.caffeine:caffeine:3.2.2")

  implementation(project(":common"))

  runtimeOnly("org.postgresql:postgresql:42.7.7")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:1.4.11")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.31") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.31")
  testImplementation("org.wiremock:wiremock-standalone:3.13.1")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:4.1.1")
  testImplementation("com.google.code.gson:gson:2.13.1")
  testImplementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.10.0")
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    freeCompilerArgs.addAll("-Xjvm-default=all", "-Xwhen-guards", "-Xannotation-default-target=param-property")
  }
}

configure<com.gorylenko.GitPropertiesPluginExtension> {
  dotGitDirectory.set(File("${project.rootDir}/.git"))
}

tasks {
  withType<KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}

data class ModelConfiguration(val name: String, val packageName: String, val url: String, val models: String = "") {
  fun toBuildModelTaskName(): String = "build${nameToCamel()}ApiModel"
  fun toWriteJsonTaskName(): String = "write${nameToCamel()}Json"
  fun toReadProductionVersionTaskName(): String = "read${nameToCamel()}ProductionVersion"
  private val snakeRegex = "-[a-zA-Z]".toRegex()
  private fun nameToCamel(): String = snakeRegex.replace(name) {
    it.value.replace("-", "").uppercase()
  }.replaceFirstChar { it.uppercase() }
  val input: String
    get() = "$projectDir/openapi-specs/$name-api-docs.json"
  val output: String
    get() = name
}

val models = listOf(
  ModelConfiguration(
    name = "alerts",
    packageName = "alerts",
    url = "https://alerts-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "Alert,AlertCodeSummary",
  ),
  ModelConfiguration(
    name = "complexity-of-need",
    packageName = "complexityofneed",
    url = "https://complexity-of-need-staging.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "ComplexityOfNeed,Level",
  ),
  ModelConfiguration(
    name = "incentives",
    packageName = "incentives",
    url = "https://incentives-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "IncentiveReviewSummary,IncentiveReviewDetail",
  ),
  //  ModelConfiguration(
  //    name = "prison-api",
  //    packageName = "prisonapi",
  //    url = "https://prison-api-dev.prison.service.justice.gov.uk/v3/api-docs",
  //  ),
  ModelConfiguration(
    name = "prison-register",
    packageName = "prisonregister",
    url = "https://prison-register-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "AddressDto,PrisonDto,PrisonOperatorDto,PrisonTypeDto",
  ),
  ModelConfiguration(
    name = "restricted-patients",
    packageName = "restrictedpatients",
    url = "https://restricted-patients-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
    models = "Agency,RestrictedPatientDto",
  ),
)

tasks {
  withType<KotlinCompile> {
    dependsOn(models.map { it.toBuildModelTaskName() })
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
  withType<KtLintCheckTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(models.map { it.toBuildModelTaskName() })
  }
  withType<KtLintFormatTask> {
    // Under gradle 8 we must declare the dependency here, even if we're not going to be linting the model
    mustRunAfter(models.map { it.toBuildModelTaskName() })
  }
}
models.forEach {
  tasks.register(it.toBuildModelTaskName(), GenerateTask::class) {
    group = "Generate model from API JSON definition"
    description = "Generate model from API JSON definition for ${it.name}"
    generatorName.set("kotlin")
    skipValidateSpec.set(true)
    inputSpec.set(it.input)
    outputDir.set("$buildDirectory/generated/${it.output}")
    modelPackage.set("uk.gov.justice.digital.hmpps.prisonersearch.indexer.${it.packageName}.model")
    apiPackage.set("uk.gov.justice.digital.hmpps.prisonersearch.indexer.${it.packageName}.api")
    configOptions.set(configValues)
    globalProperties.set(mapOf("models" to it.models))
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
  }
  tasks.register(it.toWriteJsonTaskName()) {
    group = "Write JSON"
    description = "Write JSON for ${it.name}"
    doLast {
      val json = URI.create(it.url).toURL().readText()
      val formattedJson = ObjectMapper().let { mapper ->
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json))
      }
      Files.write(Paths.get(it.input), formattedJson.toByteArray())
    }
  }
  tasks.register(it.toReadProductionVersionTaskName()) {
    group = "Read current production version"
    description = "Read current production version for ${it.name}"
    doLast {
      val productionUrl = it.url.replace("-dev".toRegex(), "")
        .replace("dev.".toRegex(), "")
        .replace("/v3/api-docs".toRegex(), "/info")
      val json = URI.create(productionUrl).toURL().readText()
      val version = ObjectMapper().readTree(json).at("/build/version").asText()
      println(version)
    }
  }
}

val buildDirectory: Directory = layout.buildDirectory.get()
val configValues = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original",
)

kotlin {
  models.map { it.output }.forEach { generatedProject ->
    sourceSets["main"].apply {
      kotlin.srcDir("$buildDirectory/generated/$generatedProject/src/main/kotlin")
    }
  }
}

configure<KtlintExtension> {
  models.map { it.output }.forEach { generatedProject ->
    filter {
      exclude {
        it.file.path.contains("$buildDirectory/generated/$generatedProject/src/main/")
      }
    }
  }
}
