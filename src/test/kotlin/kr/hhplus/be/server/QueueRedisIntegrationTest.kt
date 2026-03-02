package kr.hhplus.be.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers(disabledWithoutDocker = true)
class QueueRedisIntegrationTest : AbstractReservationIntegrationScenarioTest() {
    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @BeforeEach
    fun clearQueueKeys() {
        redisTemplate.keys("queue:concert:*")?.let {
            if (it.isNotEmpty()) {
                redisTemplate.delete(it)
            }
        }
    }

    @Test
    fun `활성 토큰이 종료되면 Redis 대기열에서 다음 사용자가 승급된다`() {
        val concert = concertRepository.findAll().first()
        val user1 = createUser()
        val user2 = createUser()

        val firstToken = queueService.issueToken(user1, concert.id!!)
        val secondToken = queueService.issueToken(user2, concert.id!!)

        assertEquals("ADMITTED", firstToken.queueStatus)
        assertEquals("WAITING", secondToken.queueStatus)

        queueService.expireAfterPayment(firstToken.queueToken)
        val nextPosition = queueService.getPosition(user2.id!!, secondToken.queueToken)

        assertEquals("ADMITTED", nextPosition.queueStatus)
        assertEquals(1L, nextPosition.currentPosition)
        assertEquals(0L, nextPosition.aheadCount)
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
        }
    }
}
