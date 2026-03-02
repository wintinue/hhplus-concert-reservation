package kr.hhplus.be.server.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class DataPlatformConfiguration {
    @Bean
    fun restClientBuilder(): RestClient.Builder = RestClient.builder()
}
