@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.Path as KotlinPath

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot")
  id("org.openapi.generator") version "7.21.0"
  kotlin("plugin.spring")
  kotlin("plugin.jpa") version "2.3.20"
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
  implementation("org.opensearch.client:spring-data-opensearch-starter:3.0.4")

  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:2.1.0")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-cache")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.springframework.data:spring-data-elasticsearch")
  implementation("org.springframework.boot:spring-boot-jackson2")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
  constraints {
    implementation("org.webjars:swagger-ui:5.32.2")
  }

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:7.3.0")
  // Leaving at version 2.9.0 to match App Insights https://github.com/microsoft/ApplicationInsights-Java/blob/3.6.2/dependencyManagement/build.gradle.kts#L16
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.26.1")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9")
  implementation("org.awaitility:awaitility-kotlin:4.3.0")
  implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

  implementation(project(":common"))

  runtimeOnly("org.postgresql:postgresql:42.7.10")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:2.1.0")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.35") {
    exclude(group = "io.swagger.core.v3")
  }
  testImplementation("io.swagger.core.v3:swagger-core-jakarta:2.2.40")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:5.1.0")
  testImplementation("com.google.code.gson:gson:2.13.2")
  testImplementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.10.0")
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
  withType<KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }
}

@CacheableTask
abstract class WriteJsonTask : DefaultTask() {
  private companion object {
    private val mapper = JsonMapper()
  }

  @get:Input
  abstract val url: Property<String>

  @get:OutputFile
  abstract val outputFile: RegularFileProperty

  @TaskAction
  fun run() {
    val json = URI.create(url.get()).toURL().readText()
    val formattedJson = mapper.let { mapper ->
      mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json))
    }
    outputFile.get().asFile.writeText(formattedJson)
    logger.lifecycle("Written ${outputFile.get()} from ${url.get()}")
  }
}

@CacheableTask
abstract class ReadProductionVersionTask : DefaultTask() {
  private companion object {
    private val mapper = JsonMapper()
  }

  @get:Input
  abstract val url: Property<String>

  @TaskAction
  fun run() {
    val productionUrl = url.get().replace("-dev".toRegex(), "")
      .replace("dev.".toRegex(), "")
      .replace("/v3/api-docs".toRegex(), "/info")
    val json = URI.create(productionUrl).toURL().readText()
    val version = mapper.readTree(json).at("/build/version").asString()
    println(version)
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

val packagePrefix = "uk.gov.justice.digital.hmpps.prisonersearch.indexer"
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
  ModelConfiguration(
    name = "nomis-prisoner",
    packageName = "nomisprisoner",
    url = "https://nomis-prisoner-api-dev.prison.service.justice.gov.uk/v3/api-docs",
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
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }
  withType<KtLintCheckTask> {
    mustRunAfter(models.map { it.toBuildModelTaskName() })
  }
  withType<KtLintFormatTask> {
    mustRunAfter(models.map { it.toBuildModelTaskName() })
  }
}
models.forEachIndexed { i, model ->
  tasks.register<GenerateTask>(model.toBuildModelTaskName()) {
    val buildDirectory: DirectoryProperty = project.layout.buildDirectory
    group = "Generate model from API JSON definition"
    description = "Generate model from API JSON definition for ${model.name}"
    generatorName.set("kotlin")
    library.set("jvm-spring-webclient")
    skipValidateSpec.set(true)
    inputSpec.set(model.input)
    outputDir.set(buildDirectory.dir("generated/${model.output}").get().asFile.path)
    modelPackage.set("$packagePrefix.${model.packageName}.model")
    apiPackage.set("$packagePrefix.${model.packageName}.api")
    configOptions.set(configValues)
    KotlinPath("$projectDir/openapi-generator-ignore-${model.name}")
      .takeIf { p -> p.exists() }?.apply { ignoreFileOverride.set(this.pathString) }
      ?: globalProperties.set(mapOf("models" to model.models))
    generateModelTests.set(false)
    generateModelDocumentation.set(false)
    mustRunAfter(model.toWriteJsonTaskName())
    // GenerateTask is not safe to run in parallel so need to ensure that only one runs at once
    if (i != 0) mustRunAfter(models[i - 1].toBuildModelTaskName())
  }
  tasks.register<WriteJsonTask>(model.toWriteJsonTaskName()) {
    val buildDirectory: DirectoryProperty = project.layout.buildDirectory
    group = "Write JSON"
    description = "Write JSON for ${model.name}"
    url.set(model.url)
    outputFile.set(buildDirectory.file(model.input))
    // ensure that the write task happens every time
    outputs.upToDateWhen { false }
  }
  tasks.register<ReadProductionVersionTask>(model.toReadProductionVersionTaskName()) {
    group = "Read current production version"
    description = "Read current production version for ${model.name}"
    url.set(model.url)
  }
}

val configValues = mapOf(
  "dateLibrary" to "java8-localdatetime",
  "serializationLibrary" to "jackson",
  "enumPropertyNaming" to "original",
  "useSpringBoot3" to "true",
)

kotlin {
  val buildDirectory: DirectoryProperty = project.layout.buildDirectory
  sourceSets["main"].apply {
    models.map { it.output }.forEach { generatedProject ->
      kotlin.srcDir(buildDirectory.dir("generated/$generatedProject/src/main/kotlin").get().toString())
    }
  }
}

ktlint {
  val buildDirectory: DirectoryProperty = project.layout.buildDirectory
  models.map { it.output }.forEach { generatedProject ->
    filter {
      exclude {
        it.file.path.contains(buildDirectory.dir("generated/$generatedProject/src/main/").get().toString())
      }
    }
  }
}
