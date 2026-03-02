package kr.hhplus.be.server

import kr.hhplus.be.server.common.ConflictException
import kr.hhplus.be.server.common.QueueExpiredException
import kr.hhplus.be.server.domain.entity.ConcertEntity
import kr.hhplus.be.server.domain.entity.QueueTokenEntity
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.enums.QueueStatus
import kr.hhplus.be.server.domain.repository.ConcertRepository
import kr.hhplus.be.server.domain.repository.QueueTokenRepository
import kr.hhplus.be.server.queue.QueueService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

class QueueServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    private val concertRepository = mockk<ConcertRepository>()
    private val queueTokenRepository = mockk<QueueTokenRepository>()
    private val redisTemplate = mockk<StringRedisTemplate>()
    private val zSetOperations = mockk<ZSetOperations<String, String>>(relaxed = true)
    private val service = QueueService(concertRepository, queueTokenRepository, redisTemplate, clock)

    @Test
    fun `첫 대기열 토큰은 즉시 ADMITTED 로 발급된다`() {
        val user = userEntity(1L)
        val concert = concertEntity()
        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { zSetOperations.zCard("queue:concert:1:waiting") } returns 0L
        every { zSetOperations.zCard("queue:concert:1:active") } returns 0L
        every { concertRepository.findByIdForUpdate(1L) } returns concert
        every { queueTokenRepository.findActiveToken(1L, 1L, listOf(QueueStatus.WAITING, QueueStatus.ADMITTED, QueueStatus.IN_PROGRESS)) } returns null
        every { queueTokenRepository.findMaxQueueNumber(1L) } returns 0L
        every { queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.ADMITTED) } returns emptyList()
        every { queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.WAITING) } returns emptyList()
        every { queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.IN_PROGRESS) } returns emptyList()
        every { queueTokenRepository.save(any()) } answers { firstArg() }

        val result = service.issueToken(user, 1L)

        assertEquals(1L, result.queueNumber)
        assertEquals("ADMITTED", result.queueStatus)
    }

    @Test
    fun `이미 활성 토큰이 있으면 중복 발급되지 않는다`() {
        val user = userEntity(1L)
        val concert = concertEntity()
        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { zSetOperations.zCard("queue:concert:1:waiting") } returns 0L
        every { zSetOperations.zCard("queue:concert:1:active") } returns 0L
        every { concertRepository.findByIdForUpdate(1L) } returns concert
        every { queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.WAITING) } returns emptyList()
        every { queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.ADMITTED) } returns emptyList()
        every { queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.IN_PROGRESS) } returns emptyList()
        every { queueTokenRepository.findActiveToken(1L, 1L, listOf(QueueStatus.WAITING, QueueStatus.ADMITTED, QueueStatus.IN_PROGRESS)) } returns
            QueueTokenEntity("token-1", user, concert, 1L, QueueStatus.WAITING, LocalDateTime.now(clock), LocalDateTime.now(clock).plusMinutes(30), null, LocalDateTime.now(clock))

        assertThrows(ConflictException::class.java) {
            service.issueToken(user, 1L)
        }
    }

    @Test
    fun `대기열 위치 조회는 앞선 대기 인원 수와 현재 순번을 반환한다`() {
        val user = userEntity(1L)
        val concert = concertEntity()
        val current = QueueTokenEntity(
            "token-2",
            user,
            concert,
            2L,
            QueueStatus.WAITING,
            LocalDateTime.now(clock),
            LocalDateTime.now(clock).plusMinutes(30),
            null,
            LocalDateTime.now(clock),
        )
        val waiting = listOf(
            QueueTokenEntity("token-1", userEntity(2L), concert, 1L, QueueStatus.WAITING, LocalDateTime.now(clock), LocalDateTime.now(clock).plusMinutes(30), null, LocalDateTime.now(clock)),
            current,
        )
        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { queueTokenRepository.findById("token-2") } returns Optional.of(current)
        every { concertRepository.findByIdForUpdate(1L) } returns concert
        every { zSetOperations.zCard("queue:concert:1:waiting") } returnsMany listOf(0L, 2L)
        every { zSetOperations.zCard("queue:concert:1:active") } returns 0L
        every { zSetOperations.rangeByScore("queue:concert:1:active", Double.NEGATIVE_INFINITY, any()) } returns emptySet<String>()
        every { zSetOperations.popMin("queue:concert:1:waiting") } returns null
        every { queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.ADMITTED) } returns emptyList()
        every { queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.WAITING) } returns waiting
        every { queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.IN_PROGRESS) } returns emptyList()
        every { zSetOperations.rank("queue:concert:1:waiting", "token-2") } returns 1L
        every { queueTokenRepository.save(any()) } answers { firstArg() }
        every { queueTokenRepository.save(current) } returns current

        val result = service.getPosition(1L, "token-2")

        assertEquals(2L, result.currentPosition)
        assertEquals(1L, result.aheadCount)
        assertEquals("WAITING", result.queueStatus)
        verify { queueTokenRepository.save(current) }
    }

    @Test
    fun `만료된 토큰의 위치 조회는 QueueExpiredException을 던진다`() {
        val user = userEntity(1L)
        val concert = concertEntity()
        val expired = QueueTokenEntity(
            "expired-token",
            user,
            concert,
            1L,
            QueueStatus.EXPIRED,
            LocalDateTime.now(clock).minusMinutes(40),
            LocalDateTime.now(clock).minusMinutes(10),
            null,
            LocalDateTime.now(clock).minusMinutes(10),
        )
        every { redisTemplate.opsForZSet() } returns zSetOperations
        every { queueTokenRepository.findById("expired-token") } returns Optional.of(expired)
        every { concertRepository.findByIdForUpdate(1L) } returns concert
        every { zSetOperations.zCard("queue:concert:1:waiting") } returns 0L
        every { zSetOperations.zCard("queue:concert:1:active") } returns 0L
        every { zSetOperations.rangeByScore("queue:concert:1:active", Double.NEGATIVE_INFINITY, any()) } returns emptySet<String>()
        every { zSetOperations.popMin("queue:concert:1:waiting") } returns null
        every { queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.ADMITTED) } returns emptyList()
        every { queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.WAITING) } returns emptyList()
        every { queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.IN_PROGRESS) } returns emptyList()

        assertThrows(QueueExpiredException::class.java) {
            service.getPosition(1L, "expired-token")
        }
    }

    private fun userEntity(id: Long) = UserEntity("user$id@test.com", "user$id", "password123").apply { this.id = id }

    private fun concertEntity() = ConcertEntity(
        title = "HH Plus Concert",
        venueName = "Olympic Hall",
        bookingOpenAt = LocalDateTime.now(clock),
        bookingCloseAt = LocalDateTime.now(clock).plusDays(5),
    ).apply { id = 1L }
}
