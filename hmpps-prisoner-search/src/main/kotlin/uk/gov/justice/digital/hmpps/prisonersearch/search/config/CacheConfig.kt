package uk.gov.justice.digital.hmpps.prisonersearch.search.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.Cache
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataAttribute
import uk.gov.justice.digital.hmpps.prisonersearch.search.services.ReferenceDataService
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching(proxyTargetClass = true)
class CacheConfig(private val referenceDataService: ReferenceDataService) : CachingConfigurer {
  @Bean
  fun referenceDataCache(): Cache = CaffeineCache(
    "referenceData",
    Caffeine.newBuilder()
      // we cache for at least an hour and next request after that will asynchronously get the new data
      .refreshAfterWrite(1, TimeUnit.HOURS)
      // throw away any results after 3 hours so that we don't serve up data that is too stale
      .expireAfterWrite(3, TimeUnit.HOURS)
      .maximumSize(ReferenceDataAttribute.entries.size + 1L)
      .recordStats()
      .build {
        referenceDataService.findReferenceData(it as ReferenceDataAttribute)
      },
  )
}
