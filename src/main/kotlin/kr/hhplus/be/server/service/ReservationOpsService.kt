package kr.hhplus.be.server.service

import kr.hhplus.be.server.api.BookingSagaResponse
import kr.hhplus.be.server.api.OutboxEventListResponse
import kr.hhplus.be.server.api.OutboxEventSummaryResponse
import kr.hhplus.be.server.common.NotFoundException
import kr.hhplus.be.server.domain.entity.BookingSagaEntity
import kr.hhplus.be.server.domain.entity.OutboxEventEntity
import kr.hhplus.be.server.domain.enums.OutboxEventStatus
import kr.hhplus.be.server.domain.repository.BookingSagaRepository
import kr.hhplus.be.server.domain.repository.OutboxEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReservationOpsService(
    private val outboxEventRepository: OutboxEventRepository,
    private val bookingSagaRepository: BookingSagaRepository,
) {
    @Transactional(readOnly = true)
    fun getOutboxEvents(status: String?, limit: Int): OutboxEventListResponse {
        val normalizedLimit = limit.coerceIn(1, 100)
        val requestedStatus = status?.takeIf { it.isNotBlank() }?.let { OutboxEventStatus.valueOf(it) }
        val items = outboxEventRepository.findAll()
            .asSequence()
            .filter { requestedStatus == null || it.eventStatus == requestedStatus }
            .sortedByDescending { it.createdAt }
            .take(normalizedLimit)
            .map { it.toResponse() }
            .toList()
        return OutboxEventListResponse(items)
    }

    @Transactional(readOnly = true)
    fun getBookingSaga(reservationId: Long): BookingSagaResponse {
        return bookingSagaRepository.findByReservationId(reservationId)?.toResponse()
            ?: throw NotFoundException("BOOKING_SAGA", reservationId)
    }
}

private fun OutboxEventEntity.toResponse() = OutboxEventSummaryResponse(
    outboxEventId = id!!,
    eventKey = eventKey,
    sagaId = sagaId,
    aggregateType = aggregateType,
    aggregateId = aggregateId,
    eventType = eventType,
    eventStatus = eventStatus.name,
    retryCount = retryCount,
    publishedAt = publishedAt,
    createdAt = createdAt!!,
    lastError = lastError,
)

private fun BookingSagaEntity.toResponse() = BookingSagaResponse(
    sagaId = sagaId,
    reservationId = reservationId,
    sagaType = sagaType,
    sagaStatus = sagaStatus.name,
    currentStep = currentStep,
    startedAt = startedAt,
    completedAt = completedAt,
    failedAt = failedAt,
    failureReason = failureReason,
)
