package kr.hhplus.be.server.reservation.domain

import java.time.LocalDateTime

data class SeatSnapshot(
    val seatId: Long,
    val scheduleId: Long,
    val price: Long,
    val status: String,
)

data class HoldSnapshot(
    val holdId: String,
    val userId: Long,
    val concertId: Long,
    val scheduleId: Long,
    val queueToken: String,
    val seatIds: List<Long>,
    val totalAmount: Long,
    val status: String,
    val holdExpiresAt: LocalDateTime,
)

data class ReservationSnapshot(
    val reservationId: Long,
    val userId: Long,
    val concertId: Long,
    val scheduleId: Long,
    val holdId: String,
    val seatIds: List<Long>,
    val totalAmount: Long,
    val status: String,
    val createdAt: LocalDateTime,
)

data class PaymentSnapshot(
    val paymentId: Long,
    val reservationId: Long,
    val amount: Long,
    val status: String,
    val paidAt: LocalDateTime,
)
