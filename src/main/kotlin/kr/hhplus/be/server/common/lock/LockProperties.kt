package kr.hhplus.be.server.common.lock

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.lock")
data class LockProperties(
    val waitTimeoutMs: Long = 3000,
    val leaseTimeoutMs: Long = 5000,
    val retryIntervalMs: Long = 50,
)
