package kr.hhplus.be.server.reservation.application

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.LocalDateTime

data class ReservationPaymentCompletedEvent(
    val reservationId: Long,
    val userId: Long,
    val concertId: Long,
    val scheduleId: Long,
    val seatIds: List<Long>,
    val paymentId: Long,
    val amount: Long,
    val method: String,
    val paidAt: LocalDateTime,
)

data class ReservationPaymentPayload(
    val reservationId: Long,
    val userId: Long,
    val concertId: Long,
    val scheduleId: Long,
    val seatIds: List<Long>,
    val paymentId: Long,
    val amount: Long,
    val method: String,
    val paidAt: LocalDateTime,
)

interface ReservationDataPlatformPort {
    fun sendReservationPayment(payload: ReservationPaymentPayload)
}

@Component
class ReservationPaymentEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    fun completed(event: ReservationPaymentCompletedEvent) {
        applicationEventPublisher.publishEvent(event)
    }
}

class ReservationPaymentEventListener
