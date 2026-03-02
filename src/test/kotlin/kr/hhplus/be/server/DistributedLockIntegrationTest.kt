package kr.hhplus.be.server

import kr.hhplus.be.server.common.lock.DistributedLockExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(classes = [ServerApplication::class])
@Testcontainers(disabledWithoutDocker = true)
class DistributedLockIntegrationTest {
    @Autowired
    private lateinit var distributedLockExecutor: DistributedLockExecutor

    @Test
    fun `same lock key is executed serially`() {
        val concurrentWorkers = AtomicInteger()
        val maxConcurrentWorkers = AtomicInteger()
        runConcurrently(2) {
            distributedLockExecutor.execute("point:user:1") {
                val active = concurrentWorkers.incrementAndGet()
                maxConcurrentWorkers.updateAndGet { current -> maxOf(current, active) }
                Thread.sleep(200)
                concurrentWorkers.decrementAndGet()
            }
        }

        assertEquals(1, maxConcurrentWorkers.get())
    }

    @Test
    fun `different lock keys can proceed in parallel`() {
        val concurrentWorkers = AtomicInteger()
        val maxConcurrentWorkers = AtomicInteger()
        runConcurrently(2) { index ->
            distributedLockExecutor.execute("point:user:${index + 1}") {
                val active = concurrentWorkers.incrementAndGet()
                maxConcurrentWorkers.updateAndGet { current -> maxOf(current, active) }
                Thread.sleep(200)
                concurrentWorkers.decrementAndGet()
            }
        }

        assertEquals(2, maxConcurrentWorkers.get())
    }

    private fun runConcurrently(threadCount: Int, task: (Int) -> Unit) {
        val executor = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        repeat(threadCount) { index ->
            executor.submit {
                ready.countDown()
                start.await()
                try {
                    task(index)
                } finally {
                    done.countDown()
                }
            }
        }
        ready.await()
        start.countDown()
        done.await()
        executor.shutdown()
    }

    companion object {
        @Container
        @JvmStatic
        val mySqlContainer: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("hhplus")
            .withUsername("test")
            .withPassword("test")

        @Container
        @JvmStatic
        val redisContainer: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mySqlContainer.getJdbcUrl() + "?characterEncoding=UTF-8&serverTimezone=UTC" }
            registry.add("spring.datasource.username") { mySqlContainer.username }
            registry.add("spring.datasource.password") { mySqlContainer.password }
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379) }
            registry.add("app.lock.wait-timeout-ms") { 1000 }
            registry.add("app.lock.lease-timeout-ms") { 1000 }
            registry.add("app.lock.retry-interval-ms") { 10 }
        }
    }
}
