package kr.hhplus.be.server.config

import kr.hhplus.be.server.common.lock.LockProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(LockProperties::class)
class LockConfiguration
