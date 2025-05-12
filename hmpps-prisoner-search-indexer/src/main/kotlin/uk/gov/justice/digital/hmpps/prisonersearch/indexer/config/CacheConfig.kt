package uk.gov.justice.digital.hmpps.prisonersearch.indexer.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.Cache
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching(proxyTargetClass = true)
class CacheConfig : CachingConfigurer {
  @Bean
  fun prisonRegisterCache(): Cache = CaffeineCache(
    "prisons",
    Caffeine.newBuilder()
      .maximumSize(1)
      // set an expiry in case the prison registry was unavailable and so a null result was cached
      .expireAfterWrite(1, TimeUnit.HOURS)
      .recordStats()
      .build(),
  )
}
