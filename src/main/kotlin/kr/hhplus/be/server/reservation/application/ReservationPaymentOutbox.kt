package kr.hhplus.be.server.reservation.application

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.domain.entity.BookingSagaEntity
import kr.hhplus.be.server.domain.entity.OutboxEventEntity
import kr.hhplus.be.server.domain.enums.BookingSagaStatus
import kr.hhplus.be.server.domain.enums.OutboxEventStatus
import kr.hhplus.be.server.domain.repository.BookingSagaRepository
import kr.hhplus.be.server.domain.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
class ReservationPaymentSagaService(
    private val bookingSagaRepository: BookingSagaRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    @Transactional
    fun start(event: ReservationPaymentCompletedEvent): String {
        val existing = bookingSagaRepository.findByReservationId(event.reservationId)
        if (existing != null) {
            return outboxEventRepository.findTop100ByEventStatusInOrderByCreatedAtAsc(listOf(OutboxEventStatus.PENDING, OutboxEventStatus.FAILED))
                .firstOrNull { it.sagaId == existing.sagaId && it.aggregateId == event.reservationId }
                ?.eventKey
                ?: existing.sagaId
        }

        val now = LocalDateTime.now(clock)
        val sagaId = "booking-saga:${event.reservationId}:${UUID.randomUUID()}"
        bookingSagaRepository.save(
            BookingSagaEntity(
                sagaId = sagaId,
                reservationId = event.reservationId,
                sagaType = "RESERVATION_PAYMENT_COMPLETION",
                sagaStatus = BookingSagaStatus.STARTED,
                currentStep = "OUTBOX_STORED",
                startedAt = now,
            ),
        )
        val outbox = outboxEventRepository.save(
            OutboxEventEntity(
                eventKey = "reservation-payment:${event.reservationId}:${UUID.randomUUID()}",
                sagaId = sagaId,
                aggregateType = "RESERVATION",
                aggregateId = event.reservationId,
                eventType = "ReservationPaymentCompleted",
                payload = objectMapper.writeValueAsString(
                    ReservationPaymentPayload(
                        reservationId = event.reservationId,
                        userId = event.userId,
                        concertId = event.concertId,
                        scheduleId = event.scheduleId,
                        seatIds = event.seatIds,
                        paymentId = event.paymentId,
                        amount = event.amount,
                        method = event.method,
                        paidAt = event.paidAt,
                    ),
                ),
                eventStatus = OutboxEventStatus.PENDING,
            ),
        )
        return outbox.eventKey
    }

    @Transactional
    fun markDispatched(sagaId: String) {
        val saga = bookingSagaRepository.findBySagaId(sagaId) ?: return
        saga.currentStep = "KAFKA_PUBLISHED"
        saga.sagaStatus = BookingSagaStatus.STARTED
        saga.completedAt = null
        saga.failedAt = null
        saga.failureReason = null
    }

    @Transactional
    fun markCompleted(sagaId: String) {
        val saga = bookingSagaRepository.findBySagaId(sagaId) ?: return
        saga.currentStep = "DATA_PLATFORM_SENT"
        saga.sagaStatus = BookingSagaStatus.COMPLETED
        saga.completedAt = LocalDateTime.now(clock)
        saga.failedAt = null
        saga.failureReason = null
    }

    @Transactional
    fun markFailed(sagaId: String, reason: String) {
        val saga = bookingSagaRepository.findBySagaId(sagaId) ?: return
        saga.currentStep = "DATA_PLATFORM_SEND_FAILED"
        saga.sagaStatus = BookingSagaStatus.FAILED
        saga.failedAt = LocalDateTime.now(clock)
        saga.failureReason = reason
    }

    @Transactional(readOnly = true)
    fun isCompleted(sagaId: String): Boolean = bookingSagaRepository.findBySagaId(sagaId)?.sagaStatus == BookingSagaStatus.COMPLETED
}

@Service
class ReservationOutboxStateService(
    private val outboxEventRepository: OutboxEventRepository,
    private val clock: Clock,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claimForPublish(eventKey: String, maxRetryCount: Int): OutboxEventEntity? {
        val claimed = outboxEventRepository.updateStatusIfCurrentIn(
            eventKey = eventKey,
            currentStatuses = listOf(OutboxEventStatus.PENDING, OutboxEventStatus.FAILED),
            nextStatus = OutboxEventStatus.PROCESSING,
        )
        if (claimed == 0) {
            return null
        }
        return outboxEventRepository.findByEventKey(eventKey)?.takeIf { it.retryCount < maxRetryCount }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markPublished(eventKey: String) {
        val outboxEvent = outboxEventRepository.findByEventKey(eventKey) ?: return
        outboxEvent.eventStatus = OutboxEventStatus.PUBLISHED
        outboxEvent.publishedAt = LocalDateTime.now(clock)
        outboxEvent.lastError = null
        outboxEventRepository.save(outboxEvent)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(eventKey: String, reason: String): Int {
        val outboxEvent = outboxEventRepository.findByEventKey(eventKey) ?: return 0
        outboxEvent.eventStatus = OutboxEventStatus.FAILED
        outboxEvent.retryCount += 1
        outboxEvent.lastError = reason
        outboxEventRepository.save(outboxEvent)
        return outboxEvent.retryCount
    }
}

@Component
class ReservationOutboxRelay(
    private val reservationPaymentMessagePublisher: ReservationPaymentMessagePublisher,
    private val reservationPaymentSagaService: ReservationPaymentSagaService,
    private val reservationOutboxStateService: ReservationOutboxStateService,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${app.outbox.max-retry-count:3}")
    private val maxRetryCount: Int,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000)
    fun publishPending() {
        outboxEventRepository.findTop100ByEventStatusInOrderByCreatedAtAsc(listOf(OutboxEventStatus.PENDING, OutboxEventStatus.FAILED))
            .filter { it.retryCount < maxRetryCount }
            .forEach { publishByEventKey(it.eventKey) }
    }

    fun publishByEventKey(eventKey: String) {
        claimForPublish(eventKey)?.let(::publishClaimed)
    }

    fun publish(outboxEvent: OutboxEventEntity) {
        if (outboxEvent.retryCount >= maxRetryCount) {
            return
        }
        val claimedOutboxEvent = if (outboxEvent.eventStatus == OutboxEventStatus.PROCESSING) {
            outboxEvent
        } else {
            claimForPublish(outboxEvent.eventKey)
        } ?: return
        publishClaimed(claimedOutboxEvent)
    }

    private fun publishClaimed(outboxEvent: OutboxEventEntity) {
        try {
            val payload = objectMapper.readValue(outboxEvent.payload, ReservationPaymentPayload::class.java)
            val result = reservationPaymentMessagePublisher.publish(
                ReservationPaymentKafkaMessage(
                    eventKey = outboxEvent.eventKey,
                    sagaId = outboxEvent.sagaId,
                    reservationId = outboxEvent.aggregateId,
                    payload = payload,
                ),
            )
            reservationOutboxStateService.markPublished(outboxEvent.eventKey)
            when (result) {
                ReservationPaymentPublishResult.DISPATCHED -> reservationPaymentSagaService.markDispatched(outboxEvent.sagaId)
                ReservationPaymentPublishResult.COMPLETED -> reservationPaymentSagaService.markCompleted(outboxEvent.sagaId)
            }
        } catch (ex: RuntimeException) {
            val retryCount = reservationOutboxStateService.markFailed(outboxEvent.eventKey, ex.message ?: ex.javaClass.simpleName)
            reservationPaymentSagaService.markFailed(outboxEvent.sagaId, ex.message ?: ex.javaClass.simpleName)
            logger.warn("failed to publish outbox event. eventKey={}, retryCount={}", outboxEvent.eventKey, retryCount)
            throw ex
        }
    }

    private fun claimForPublish(eventKey: String): OutboxEventEntity? = reservationOutboxStateService.claimForPublish(eventKey, maxRetryCount)
}
