package kr.hhplus.be.server.concert.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ConcertConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
