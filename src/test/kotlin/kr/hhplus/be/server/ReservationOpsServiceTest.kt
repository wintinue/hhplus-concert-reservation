package kr.hhplus.be.server

import kr.hhplus.be.server.domain.entity.BookingSagaEntity
import kr.hhplus.be.server.domain.entity.OutboxEventEntity
import kr.hhplus.be.server.domain.enums.BookingSagaStatus
import kr.hhplus.be.server.domain.enums.OutboxEventStatus
import kr.hhplus.be.server.domain.repository.BookingSagaRepository
import kr.hhplus.be.server.domain.repository.OutboxEventRepository
import kr.hhplus.be.server.service.ReservationOpsService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ReservationOpsServiceTest {
    @Test
    fun `outbox 이벤트 조회는 상태 필터와 limit를 적용한다`() {
        val outboxRepository = mockk<OutboxEventRepository>()
        val sagaRepository = mockk<BookingSagaRepository>(relaxed = true)
        val service = ReservationOpsService(outboxRepository, sagaRepository)
        val older = outboxEvent(1L, "old", OutboxEventStatus.FAILED, createdAt = LocalDateTime.of(2026, 3, 1, 0, 0))
        val newer = outboxEvent(2L, "new", OutboxEventStatus.FAILED, createdAt = LocalDateTime.of(2026, 3, 1, 0, 1))
        val published = outboxEvent(3L, "published", OutboxEventStatus.PUBLISHED, createdAt = LocalDateTime.of(2026, 3, 1, 0, 2))

        every { outboxRepository.findAll() } returns listOf(older, newer, published)

        val result = service.getOutboxEvents("FAILED", 1)

        assertEquals(1, result.items.size)
        assertEquals(2L, result.items.single().outboxEventId)
    }

    @Test
    fun `booking saga 조회는 reservation 기준 응답을 반환한다`() {
        val outboxRepository = mockk<OutboxEventRepository>(relaxed = true)
        val sagaRepository = mockk<BookingSagaRepository>()
        val service = ReservationOpsService(outboxRepository, sagaRepository)
        val saga = BookingSagaEntity(
            sagaId = "saga-1",
            reservationId = 10L,
            sagaType = "RESERVATION_PAYMENT_COMPLETION",
            sagaStatus = BookingSagaStatus.COMPLETED,
            currentStep = "DATA_PLATFORM_SENT",
            startedAt = LocalDateTime.of(2026, 3, 1, 0, 0),
            completedAt = LocalDateTime.of(2026, 3, 1, 0, 1),
        )

        every { sagaRepository.findByReservationId(10L) } returns saga

        val result = service.getBookingSaga(10L)

        assertEquals("saga-1", result.sagaId)
        assertEquals("COMPLETED", result.sagaStatus)
    }

    private fun outboxEvent(
        id: Long,
        eventKey: String,
        status: OutboxEventStatus,
        createdAt: LocalDateTime,
    ): OutboxEventEntity {
        val entity = OutboxEventEntity(
            eventKey = eventKey,
            sagaId = "saga-$id",
            aggregateType = "RESERVATION",
            aggregateId = id,
            eventType = "ReservationPaymentCompleted",
            payload = "{}",
            eventStatus = status,
        )
        entity.id = id
        entity.createdAt = createdAt
        entity.updatedAt = createdAt
        return entity
    }
}
