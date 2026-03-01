package kr.hhplus.be.server.config.jpa

import org.springframework.context.annotation.Configuration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaAuditing
@EntityScan(basePackages = ["kr.hhplus.be.server.concert.domain.entity"])
@EnableJpaRepositories(basePackages = ["kr.hhplus.be.server.concert.domain.repository"])
class JpaConfig
