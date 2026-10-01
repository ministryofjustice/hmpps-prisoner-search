plugins {
    id("uk.gov.justice.hmpps.gradle-spring-boot") version "11.0.11" apply false
    kotlin("plugin.spring") version "2.4.20" apply false
}

// TODO temporarily pinned due to CVEs - try removing this when upgrading spring boot and running task dependencyCheckVersion
extra["elasticsearch-client.version"] = "9.4.7"
