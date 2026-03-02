package kr.hhplus.be.server.common.lock

import kr.hhplus.be.server.common.ConflictException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class RedisSpinLockExecutor(
    private val redisTemplate: StringRedisTemplate,
    private val lockProperties: LockProperties,
) : DistributedLockExecutor {
    override fun <T> execute(key: String, action: () -> T): T {
        val lockValue = UUID.randomUUID().toString()
        val deadline = System.nanoTime() + Duration.ofMillis(lockProperties.waitTimeoutMs).toNanos()
        while (System.nanoTime() < deadline) {
            val acquired = redisTemplate.opsForValue().setIfAbsent(
                namespaced(key),
                lockValue,
                Duration.ofMillis(lockProperties.leaseTimeoutMs),
            ) == true
            if (acquired) {
                try {
                    return action()
                } finally {
                    release(key, lockValue)
                }
            }
            Thread.sleep(lockProperties.retryIntervalMs)
        }
        throw ConflictException(
            "다른 요청이 동일한 자원을 처리 중입니다. 잠시 후 다시 시도해 주세요.",
            conflictType = "DISTRIBUTED_LOCK_UNAVAILABLE",
            retryable = true,
            extra = mapOf("lockKey" to key),
        )
    }

    private fun release(key: String, lockValue: String) {
        redisTemplate.execute(
            RELEASE_SCRIPT,
            listOf(namespaced(key)),
            lockValue,
        )
    }

    private fun namespaced(key: String): String = "lock:$key"

    companion object {
        private val RELEASE_SCRIPT = DefaultRedisScript(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}
