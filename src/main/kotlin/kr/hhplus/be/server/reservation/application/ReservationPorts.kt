package kr.hhplus.be.server.reservation.application

import kr.hhplus.be.server.api.HoldSeatResponse
import kr.hhplus.be.server.api.PaymentResponse
import kr.hhplus.be.server.api.ReservationListResponse
import kr.hhplus.be.server.api.ReservationResponse
import kr.hhplus.be.server.reservation.domain.HoldSnapshot
import kr.hhplus.be.server.reservation.domain.PaymentSnapshot
import kr.hhplus.be.server.reservation.domain.ReservationSnapshot
import kr.hhplus.be.server.reservation.domain.SeatSnapshot
import java.time.LocalDateTime

interface ReservationQueuePort {
    fun validateForRead(queueToken: String, userId: Long, concertId: Long? = null)
    fun validateForWrite(queueToken: String, userId: Long, concertId: Long? = null): String
    fun expireAfterPayment(queueToken: String)
}

interface SeatLoadPort {
    fun getScheduleConcertId(scheduleId: Long): Long
    fun getSeatsForUpdate(seatIds: List<Long>): List<SeatSnapshot>
    fun markSeatsHeld(seatIds: List<Long>)
    fun markSeatsSold(seatIds: List<Long>)
    fun markSeatsAvailable(seatIds: List<Long>)
}

interface HoldPort {
    fun expireActiveHolds(now: LocalDateTime)
    fun createHold(userId: Long, scheduleId: Long, queueToken: String, seatIds: List<Long>, totalAmount: Long, expiresAt: LocalDateTime): HoldSnapshot
    fun getHold(holdId: String): HoldSnapshot?
    fun getHoldForUpdate(holdId: String): HoldSnapshot?
    fun markHoldConfirmed(holdId: String)
    fun markHoldExpired(holdId: String)
}

interface ReservationPort {
    fun existsByHoldId(holdId: String): Boolean
    fun createReservation(userId: Long, scheduleId: Long, holdId: String, totalAmount: Long, seatIds: List<Long>, createdAt: LocalDateTime): ReservationSnapshot
    fun getReservation(reservationId: Long): ReservationSnapshot?
    fun getReservationForUpdate(reservationId: Long): ReservationSnapshot?
    fun getReservations(userId: Long, page: Int, size: Int): ReservationListResponse
    fun markReservationConfirmed(reservationId: Long)
    fun markReservationCanceled(reservationId: Long, canceledAt: LocalDateTime)
    fun markReservationExpiredByHold(holdId: String, expiredAt: LocalDateTime)
    fun expirePendingReservations(now: LocalDateTime)
}

interface PaymentPort {
    fun hasSuccessfulPayment(reservationId: Long): Boolean
    fun createSuccessPayment(reservationId: Long, amount: Long, method: String, paidAt: LocalDateTime): PaymentSnapshot
    fun createFailedPayment(reservationId: Long, amount: Long, method: String, failedAt: LocalDateTime, reason: String): PaymentSnapshot
    fun cancelSuccessPayment(reservationId: Long, canceledAt: LocalDateTime): Boolean
}

interface PointPort {
    fun deduct(userId: Long, amount: Long, now: LocalDateTime)
    fun refund(userId: Long, amount: Long, now: LocalDateTime)
}
