package uk.gov.justice.digital.hmpps.prisonersearch.indexer.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@EnableCaching(proxyTargetClass = true)
class CacheConfig : CachingConfigurer {
  @Bean
  override fun cacheManager(): CacheManager = SimpleCacheManager()
    .apply {
      setCaches(
        listOf(
          CaffeineCache(
            "prisons",
            Caffeine.newBuilder()
              .maximumSize(1)
              // set an expiry in case the prison registry was unavailable and so a null result was cached
              .expireAfterWrite(Duration.ofHours(1))
              .recordStats()
              .build(),
          ),
        ),
      )
    }
}
