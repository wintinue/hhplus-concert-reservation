package kr.hhplus.be.server.api

import java.time.LocalDateTime

data class SignUpRequest(
    val name: String,
    val email: String,
    val password: String,
)

data class LoginRequest(
    val email: String,
    val password: String,
)

data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

data class ConcertSummary(
    val concertId: Long,
    val title: String,
    val venueName: String,
    val bookingOpenAt: LocalDateTime,
    val bookingCloseAt: LocalDateTime,
)

data class ConcertListResponse(
    val items: List<ConcertSummary>,
    val page: Int,
    val size: Int,
    val total: Long,
)

data class FastSoldOutConcertSummary(
    val rank: Int,
    val concertId: Long,
    val scheduleId: Long,
    val title: String,
    val venueName: String,
    val bookingOpenAt: LocalDateTime,
    val soldOutAt: LocalDateTime,
    val soldOutSeconds: Long,
)

data class FastSoldOutConcertListResponse(
    val items: List<FastSoldOutConcertSummary>,
)

data class ScheduleSummary(
    val scheduleId: Long,
    val startAt: LocalDateTime,
    val totalSeat: Int,
    val availableSeat: Int,
)

data class ScheduleListResponse(
    val items: List<ScheduleSummary>,
)

data class SeatSummary(
    val seatId: Long,
    val section: String,
    val row: String,
    val number: String,
    val price: Long,
    val status: String,
)

data class SeatListResponse(
    val items: List<SeatSummary>,
)

data class QueueTokenIssueRequest(
    val concertId: Long,
)

data class QueueTokenIssueResponse(
    val queueToken: String,
    val concertId: Long,
    val queueNumber: Long,
    val queueStatus: String,
    val issuedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val pollingIntervalSeconds: Long,
)

data class QueuePositionResponse(
    val queueToken: String,
    val concertId: Long,
    val queueNumber: Long,
    val currentPosition: Long,
    val aheadCount: Long,
    val totalWaitingCount: Long,
    val estimatedWaitSeconds: Long,
    val remainingWaitSeconds: Long,
    val queueStatus: String,
    val canReserve: Boolean,
    val tokenExpiresAt: LocalDateTime,
    val tokenRemainingSeconds: Long,
    val reservationWindowExpiresAt: LocalDateTime?,
    val reservationWindowRemainingSeconds: Long,
    val refreshedAt: LocalDateTime,
)

data class HoldSeatRequest(
    val scheduleId: Long,
    val seatIds: List<Long>,
)

data class HoldSeatResponse(
    val holdId: String,
    val holdExpiresAt: LocalDateTime,
    val queueToken: String,
    val seatIds: List<Long>,
    val totalAmount: Long,
)

data class CreateReservationRequest(
    val holdId: String,
)

data class CancelReservationRequest(
    val status: String,
    val reason: String? = null,
)

data class ReservationResponse(
    val reservationId: Long,
    val userId: Long,
    val scheduleId: Long,
    val seatIds: List<Long>,
    val totalAmount: Long,
    val status: String,
    val createdAt: LocalDateTime,
)

data class ReservationListResponse(
    val items: List<ReservationResponse>,
    val page: Int,
    val size: Int,
    val total: Long,
)

data class PointChargeRequest(
    val amount: Long,
)

data class PointBalanceResponse(
    val userId: Long,
    val balance: Long,
    val updatedAt: LocalDateTime,
)

data class PointChargeResponse(
    val transactionId: String,
    val userId: Long,
    val chargedAmount: Long,
    val balanceAfter: Long,
    val chargedAt: LocalDateTime,
)

data class PaymentRequest(
    val reservationId: Long,
    val amount: Long,
    val method: String,
)

data class PaymentResponse(
    val paymentId: Long,
    val reservationId: Long,
    val amount: Long,
    val status: String,
    val paidAt: LocalDateTime,
)
