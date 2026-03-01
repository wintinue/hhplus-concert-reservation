package kr.hhplus.be.server.reservation.application

import kr.hhplus.be.server.api.HoldSeatResponse
import kr.hhplus.be.server.api.PaymentResponse
import kr.hhplus.be.server.api.ReservationListResponse
import kr.hhplus.be.server.api.ReservationResponse
import kr.hhplus.be.server.common.ConflictException
import kr.hhplus.be.server.common.ForbiddenException
import kr.hhplus.be.server.common.NotFoundException
import kr.hhplus.be.server.common.ValidationException
import kr.hhplus.be.server.reservation.domain.ReservationSnapshot
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

@Service
class HoldSeatsUseCase(
    private val queuePort: ReservationQueuePort,
    private val seatLoadPort: SeatLoadPort,
    private val holdPort: HoldPort,
    private val reservationPort: ReservationPort,
    private val clock: Clock,
) {
    fun execute(userId: Long, queueToken: String, scheduleId: Long, seatIds: List<Long>): HoldSeatResponse {
        val deduplicatedSeatIds = seatIds.distinct()
        if (deduplicatedSeatIds.isEmpty()) {
            throw ValidationException("좌석은 1개 이상 선택해야 합니다.", listOf(mapOf("field" to "seatIds", "reason" to "must not be empty")))
        }
        val now = LocalDateTime.now(clock)
        holdPort.expireActiveHolds(now)
        reservationPort.expirePendingReservations(now)
        val concertId = seatLoadPort.getScheduleConcertId(scheduleId)
        val validatedQueueToken = queuePort.validateForWrite(queueToken, userId, concertId)
        val seats = seatLoadPort.getSeatsForUpdate(deduplicatedSeatIds)
        if (seats.size != deduplicatedSeatIds.size) throw NotFoundException("SEAT")

        val unavailable = seats.filter { it.scheduleId != scheduleId || it.status != "AVAILABLE" }.map { it.seatId }
        if (unavailable.isNotEmpty()) {
            throw ConflictException(
                "이미 선점되었거나 판매된 좌석이 포함되어 있습니다.",
                conflictType = "SEAT_CONFLICT",
                retryable = false,
                extra = mapOf("unavailableSeatIds" to unavailable),
            )
        }

        seatLoadPort.markSeatsHeld(deduplicatedSeatIds)
        val hold = holdPort.createHold(
            userId = userId,
            scheduleId = scheduleId,
            queueToken = validatedQueueToken,
            seatIds = deduplicatedSeatIds,
            totalAmount = seats.sumOf { it.price },
            expiresAt = now.plusMinutes(5),
        )
        return HoldSeatResponse(hold.holdId, hold.holdExpiresAt, hold.queueToken, hold.seatIds, hold.totalAmount)
    }
}

@Service
class CreateReservationUseCase(
    private val queuePort: ReservationQueuePort,
    private val holdPort: HoldPort,
    private val reservationPort: ReservationPort,
    private val clock: Clock,
) {
    fun execute(userId: Long, queueToken: String, holdId: String): ReservationResponse {
        val now = LocalDateTime.now(clock)
        holdPort.expireActiveHolds(now)
        reservationPort.expirePendingReservations(now)
        val hold = holdPort.getHold(holdId) ?: throw NotFoundException("HOLD", holdId)
        queuePort.validateForWrite(queueToken, userId, hold.concertId)
        validateOwnership(hold.userId, userId, "다른 사용자의 hold 입니다.")
        if (hold.status != "ACTIVE" || hold.holdExpiresAt.isBefore(now)) {
            throw ConflictException(
                "선점이 만료되었거나 유효하지 않습니다.",
                conflictType = "HOLD_EXPIRED",
                extra = mapOf("holdId" to hold.holdId, "holdExpiredAt" to hold.holdExpiresAt),
            )
        }
        if (reservationPort.existsByHoldId(holdId)) {
            throw ConflictException("이미 예약으로 확정된 hold 입니다.", conflictType = "HOLD_ALREADY_CONFIRMED")
        }

        holdPort.markHoldConfirmed(holdId)
        val reservation = reservationPort.createReservation(userId, hold.scheduleId, holdId, hold.totalAmount, hold.seatIds, now)
        return reservation.toResponse()
    }
}

@Service
class GetReservationsUseCase(
    private val queuePort: ReservationQueuePort,
    private val reservationPort: ReservationPort,
    private val clock: Clock,
) {
    fun execute(userId: Long, queueToken: String, page: Int, size: Int): ReservationListResponse {
        reservationPort.expirePendingReservations(LocalDateTime.now(clock))
        queuePort.validateForRead(queueToken, userId)
        return reservationPort.getReservations(userId, page, size)
    }
}

@Service
class GetReservationUseCase(
    private val queuePort: ReservationQueuePort,
    private val reservationPort: ReservationPort,
    private val clock: Clock,
) {
    fun execute(userId: Long, queueToken: String, reservationId: Long): ReservationResponse {
        reservationPort.expirePendingReservations(LocalDateTime.now(clock))
        val reservation = reservationPort.getReservation(reservationId) ?: throw NotFoundException("RESERVATION", reservationId)
        queuePort.validateForRead(queueToken, userId, reservation.concertId)
        validateOwnership(reservation.userId, userId, "다른 사용자의 예약입니다.")
        return reservation.toResponse()
    }
}

@Service
class CancelReservationUseCase(
    private val queuePort: ReservationQueuePort,
    private val reservationPort: ReservationPort,
    private val paymentPort: PaymentPort,
    private val pointPort: PointPort,
    private val seatLoadPort: SeatLoadPort,
    private val clock: Clock,
) {
    fun execute(userId: Long, queueToken: String, reservationId: Long, status: String): ReservationResponse {
        val now = LocalDateTime.now(clock)
        reservationPort.expirePendingReservations(now)
        if (status != "CANCELED") {
            throw ValidationException("예약 취소 요청이 올바르지 않습니다.", listOf(mapOf("field" to "status", "reason" to "must be CANCELED")))
        }
        val reservation = reservationPort.getReservationForUpdate(reservationId) ?: throw NotFoundException("RESERVATION", reservationId)
        queuePort.validateForWrite(queueToken, userId, reservation.concertId)
        validateOwnership(reservation.userId, userId, "다른 사용자의 예약입니다.")
        if (reservation.status == "CANCELED") throw ConflictException("이미 취소된 예약입니다.", conflictType = "RESERVATION_STATE")
        if (reservation.status == "EXPIRED") throw ConflictException("만료된 예약은 취소할 수 없습니다.", conflictType = "RESERVATION_STATE")

        seatLoadPort.markSeatsAvailable(reservation.seatIds)
        if (paymentPort.cancelSuccessPayment(reservationId, now)) {
            pointPort.refund(userId, reservation.totalAmount, now)
        }
        reservationPort.markReservationCanceled(reservationId, now)
        return reservationPort.getReservation(reservationId)!!.toResponse()
    }
}

@Service
class PayReservationUseCase(
    private val queuePort: ReservationQueuePort,
    private val reservationPort: ReservationPort,
    private val paymentPort: PaymentPort,
    private val pointPort: PointPort,
    private val seatLoadPort: SeatLoadPort,
    private val holdPort: HoldPort,
    private val clock: Clock,
) {
    fun execute(userId: Long, queueToken: String, reservationId: Long, amount: Long, method: String): PaymentResponse {
        val now = LocalDateTime.now(clock)
        reservationPort.expirePendingReservations(now)
        val reservation = reservationPort.getReservationForUpdate(reservationId) ?: throw NotFoundException("RESERVATION", reservationId)
        val validatedQueueToken = queuePort.validateForWrite(queueToken, userId, reservation.concertId)
        validateOwnership(reservation.userId, userId, "다른 사용자의 예약입니다.")
        if (reservation.status != "PENDING_PAYMENT") {
            throw ConflictException("결제 가능한 예약 상태가 아닙니다.", conflictType = "RESERVATION_STATE")
        }
        if (reservation.totalAmount != amount) {
            throw ValidationException("결제 금액이 예약 총액과 다릅니다.", listOf(mapOf("field" to "amount", "reason" to "must match reservation totalAmount")))
        }
        if (paymentPort.hasSuccessfulPayment(reservationId)) {
            throw ConflictException("이미 결제 완료된 예약입니다.", conflictType = "PAYMENT_STATE")
        }

        try {
            pointPort.deduct(userId, amount, now)
            reservationPort.markReservationConfirmed(reservationId)
            seatLoadPort.markSeatsSold(reservation.seatIds)
            val payment = paymentPort.createSuccessPayment(reservationId, amount, method, now)
            queuePort.expireAfterPayment(validatedQueueToken)
            return PaymentResponse(payment.paymentId, payment.reservationId, payment.amount, payment.status, payment.paidAt)
        } catch (ex: RuntimeException) {
            paymentPort.createFailedPayment(reservationId, amount, method, now, ex.message ?: "payment failed")
            seatLoadPort.markSeatsAvailable(reservation.seatIds)
            holdPort.markHoldExpired(reservation.holdId)
            reservationPort.markReservationExpiredByHold(reservation.holdId, now)
            throw ex
        }
    }
}

private fun validateOwnership(ownerId: Long, requesterId: Long, message: String) {
    if (ownerId != requesterId) throw ForbiddenException(message)
}

private fun ReservationSnapshot.toResponse() = ReservationResponse(
    reservationId = reservationId,
    userId = userId,
    scheduleId = scheduleId,
    seatIds = seatIds,
    totalAmount = totalAmount,
    status = status,
    createdAt = createdAt,
)
