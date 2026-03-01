package kr.hhplus.be.server

import kr.hhplus.be.server.common.ConflictException
import kr.hhplus.be.server.domain.entity.ConcertEntity
import kr.hhplus.be.server.domain.entity.QueueTokenEntity
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.enums.QueueStatus
import kr.hhplus.be.server.domain.repository.ConcertRepository
import kr.hhplus.be.server.domain.repository.QueueTokenRepository
import kr.hhplus.be.server.queue.QueueService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

class QueueServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    private val concertRepository = mock(ConcertRepository::class.java)
    private val queueTokenRepository = mock(QueueTokenRepository::class.java)
    private val service = QueueService(concertRepository, queueTokenRepository, clock)

    @Test
    fun `첫 대기열 토큰은 즉시 ADMITTED 로 발급된다`() {
        val user = userEntity(1L)
        val concert = concertEntity()
        `when`(concertRepository.findByIdForUpdate(1L)).thenReturn(concert)
        `when`(queueTokenRepository.findActiveToken(1L, 1L, listOf(QueueStatus.WAITING, QueueStatus.ADMITTED, QueueStatus.IN_PROGRESS))).thenReturn(null)
        `when`(queueTokenRepository.findMaxQueueNumber(1L)).thenReturn(0L)
        `when`(queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(1L, QueueStatus.ADMITTED)).thenReturn(emptyList())
        `when`(queueTokenRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer { it.arguments[0] }

        val result = service.issueToken(user, 1L)

        assertEquals(1L, result.queueNumber)
        assertEquals("ADMITTED", result.queueStatus)
    }

    @Test
    fun `이미 활성 토큰이 있으면 중복 발급되지 않는다`() {
        val user = userEntity(1L)
        val concert = concertEntity()
        `when`(concertRepository.findByIdForUpdate(1L)).thenReturn(concert)
        `when`(queueTokenRepository.findActiveToken(1L, 1L, listOf(QueueStatus.WAITING, QueueStatus.ADMITTED, QueueStatus.IN_PROGRESS))).thenReturn(
            QueueTokenEntity("token-1", user, concert, 1L, QueueStatus.WAITING, LocalDateTime.now(clock), LocalDateTime.now(clock).plusMinutes(30), null, LocalDateTime.now(clock)),
        )

        assertThrows(ConflictException::class.java) {
            service.issueToken(user, 1L)
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
