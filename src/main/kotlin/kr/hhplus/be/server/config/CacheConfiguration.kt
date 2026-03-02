package kr.hhplus.be.server.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfiguration {
    @Bean
    fun cacheManager(
        redisConnectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): CacheManager {
        val serializer = GenericJackson2JsonRedisSerializer(
            objectMapper.copy().registerModule(JavaTimeModule()),
        )
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
            .entryTtl(Duration.ofMinutes(3))

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration(
                "concert-list",
                defaultConfig.entryTtl(Duration.ofMinutes(10)),
            )
            .withCacheConfiguration(
                "concert-schedule",
                defaultConfig.entryTtl(Duration.ofSeconds(30)),
            )
            .withCacheConfiguration(
                "schedule-seat",
                defaultConfig.entryTtl(Duration.ofSeconds(15)),
            )
            .build()
    }
}
