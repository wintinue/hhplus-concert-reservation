package kr.hhplus.be.server.reservation.infra

import kr.hhplus.be.server.api.ReservationListResponse
import kr.hhplus.be.server.api.ReservationResponse
import kr.hhplus.be.server.common.cache.ConcertCacheService
import kr.hhplus.be.server.common.ConflictException
import kr.hhplus.be.server.common.NotFoundException
import kr.hhplus.be.server.domain.entity.PaymentEntity
import kr.hhplus.be.server.domain.entity.PointTransactionEntity
import kr.hhplus.be.server.domain.entity.ReservationEntity
import kr.hhplus.be.server.domain.entity.ReservationItemEntity
import kr.hhplus.be.server.domain.entity.SeatHoldEntity
import kr.hhplus.be.server.domain.entity.SeatHoldItemEntity
import kr.hhplus.be.server.domain.enums.HoldStatus
import kr.hhplus.be.server.domain.enums.PaymentMethod
import kr.hhplus.be.server.domain.enums.PaymentStatus
import kr.hhplus.be.server.domain.enums.PointTransactionType
import kr.hhplus.be.server.domain.enums.ReservationStatus
import kr.hhplus.be.server.domain.enums.SeatStatus
import kr.hhplus.be.server.domain.repository.PaymentRepository
import kr.hhplus.be.server.domain.repository.PointTransactionRepository
import kr.hhplus.be.server.domain.repository.ReservationItemRepository
import kr.hhplus.be.server.domain.repository.ReservationRepository
import kr.hhplus.be.server.domain.repository.ConcertScheduleRepository
import kr.hhplus.be.server.domain.repository.QueueTokenRepository
import kr.hhplus.be.server.domain.repository.SeatHoldItemRepository
import kr.hhplus.be.server.domain.repository.SeatHoldRepository
import kr.hhplus.be.server.domain.repository.SeatRepository
import kr.hhplus.be.server.domain.repository.UserPointRepository
import kr.hhplus.be.server.domain.repository.UserRepository
import kr.hhplus.be.server.queue.QueueService
import kr.hhplus.be.server.reservation.application.HoldPort
import kr.hhplus.be.server.reservation.application.PaymentPort
import kr.hhplus.be.server.reservation.application.PointPort
import kr.hhplus.be.server.reservation.application.ReservationPort
import kr.hhplus.be.server.reservation.application.ReservationQueuePort
import kr.hhplus.be.server.reservation.application.SeatLoadPort
import kr.hhplus.be.server.reservation.domain.HoldSnapshot
import kr.hhplus.be.server.reservation.domain.PaymentSnapshot
import kr.hhplus.be.server.reservation.domain.ReservationSnapshot
import kr.hhplus.be.server.reservation.domain.SeatSnapshot
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

@Component
class ReservationPersistenceAdapter(
    private val seatRepository: SeatRepository,
    private val scheduleRepository: ConcertScheduleRepository,
    private val queueTokenRepository: QueueTokenRepository,
    private val seatHoldRepository: SeatHoldRepository,
    private val seatHoldItemRepository: SeatHoldItemRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationItemRepository: ReservationItemRepository,
    private val paymentRepository: PaymentRepository,
    private val userPointRepository: UserPointRepository,
    private val pointTransactionRepository: PointTransactionRepository,
    private val userRepository: UserRepository,
    private val queueService: QueueService,
    private val concertCacheService: ConcertCacheService,
) : ReservationQueuePort, SeatLoadPort, HoldPort, ReservationPort, PaymentPort, PointPort {
    override fun validateForRead(queueToken: String, userId: Long, concertId: Long?) {
        queueService.validateQueueTokenForRead(queueToken, userId, concertId)
    }

    override fun validateForWrite(queueToken: String, userId: Long, concertId: Long?): String =
        queueService.validateReservableToken(queueToken, userId, concertId).queueToken

    override fun expireAfterPayment(queueToken: String) {
        queueService.expireAfterPayment(queueToken)
    }

    override fun getScheduleConcertId(scheduleId: Long): Long =
        scheduleRepository.findById(scheduleId).orElseThrow { NotFoundException("SCHEDULE", scheduleId) }.concert.id!!

    override fun getSeatsForUpdate(seatIds: List<Long>): List<SeatSnapshot> =
        seatRepository.findAllForUpdate(seatIds).map { SeatSnapshot(it.id!!, it.schedule.id!!, it.price, it.seatStatus.name) }

    override fun markSeatsHeld(seatIds: List<Long>) {
        val seats = seatRepository.findAllForUpdate(seatIds)
        seats.forEach { it.seatStatus = SeatStatus.HELD }
        evictScheduleCaches(seats)
    }

    override fun markSeatsSold(seatIds: List<Long>) {
        val seats = seatRepository.findAllForUpdate(seatIds)
        seats.forEach { it.seatStatus = SeatStatus.SOLD }
        evictScheduleCaches(seats)
    }

    override fun markSeatsAvailable(seatIds: List<Long>) {
        val seats = seatRepository.findAllForUpdate(seatIds)
        seats.forEach { it.seatStatus = SeatStatus.AVAILABLE }
        evictScheduleCaches(seats)
    }

    override fun expireActiveHolds(now: LocalDateTime) {
        // TODO: 현재는 MySQL 만료 스캔으로 hold를 정리한다. 문서의 목표 아키텍처에 맞추려면 추후 Redis TTL 기반 hold 관리로 전환이 필요하다.
        seatHoldRepository.findExpiredHolds(HoldStatus.ACTIVE, now)
            .forEach { hold ->
                hold.holdStatus = HoldStatus.EXPIRED
                val seatIds = seatHoldItemRepository.findSeatIdsByHoldId(hold.holdId)
                markSeatsAvailable(seatIds)
                markReservationExpiredByHold(hold.holdId, now)
            }
    }

    override fun createHold(userId: Long, scheduleId: Long, queueToken: String, seatIds: List<Long>, totalAmount: Long, expiresAt: LocalDateTime): HoldSnapshot {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("USER", userId) }
        val seats = seatRepository.findAllForUpdate(seatIds)
        val schedule = seats.firstOrNull()?.schedule ?: throw NotFoundException("SCHEDULE", scheduleId)
        val queue = queueTokenRepository.findByIdForUpdate(queueToken) ?: throw NotFoundException("QUEUE_TOKEN", queueToken)
        val hold = seatHoldRepository.save(
            SeatHoldEntity(
                holdId = UUID.randomUUID().toString(),
                user = user,
                schedule = schedule,
                queueToken = queue,
                holdStatus = HoldStatus.ACTIVE,
                totalAmount = totalAmount,
                holdExpiresAt = expiresAt,
            ),
        )
        seatHoldItemRepository.saveAll(seats.map { SeatHoldItemEntity(hold, it, it.price) })
        return hold.toSnapshot(seatIds)
    }

    override fun getHold(holdId: String): HoldSnapshot? {
        val hold = seatHoldRepository.findById(holdId).orElse(null) ?: return null
        val seatIds = seatHoldItemRepository.findSeatIdsByHoldId(holdId)
        return hold.toSnapshot(seatIds)
    }

    override fun getHoldForUpdate(holdId: String): HoldSnapshot? {
        val hold = seatHoldRepository.findByIdForUpdate(holdId) ?: return null
        val seatIds = seatHoldItemRepository.findSeatIdsByHoldId(holdId)
        return hold.toSnapshot(seatIds)
    }

    override fun markHoldConfirmed(holdId: String) {
        val hold = seatHoldRepository.findByIdForUpdate(holdId) ?: throw NotFoundException("HOLD", holdId)
        hold.holdStatus = HoldStatus.CONFIRMED
    }

    override fun markHoldExpired(holdId: String) {
        val hold = seatHoldRepository.findByIdForUpdate(holdId) ?: throw NotFoundException("HOLD", holdId)
        hold.holdStatus = HoldStatus.EXPIRED
    }

    override fun existsByHoldId(holdId: String): Boolean = reservationRepository.existsByHoldHoldId(holdId)

    override fun createReservation(userId: Long, scheduleId: Long, holdId: String, totalAmount: Long, seatIds: List<Long>, createdAt: LocalDateTime): ReservationSnapshot {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("USER", userId) }
        val hold = seatHoldRepository.findById(holdId).orElseThrow { NotFoundException("HOLD", holdId) }
        return try {
            val reservation = reservationRepository.save(
                ReservationEntity(
                    user = user,
                    schedule = hold.schedule,
                    hold = hold,
                    totalAmount = totalAmount,
                    reservationStatus = ReservationStatus.PENDING_PAYMENT,
                ),
            )
            val seats = seatRepository.findAllForUpdate(seatIds)
            reservationItemRepository.saveAll(seats.map { ReservationItemEntity(reservation, it, it.price) })
            reservation.toSnapshot(seatIds)
        } catch (ex: DataIntegrityViolationException) {
            throw ConflictException("이미 예약으로 확정된 hold 입니다.", conflictType = "HOLD_ALREADY_CONFIRMED")
        }
    }

    override fun getReservation(reservationId: Long): ReservationSnapshot? =
        reservationRepository.findById(reservationId).orElse(null)?.let { it.toSnapshot(reservationItemRepository.findSeatIdsByReservationId(reservationId)) }

    override fun getReservationForUpdate(reservationId: Long): ReservationSnapshot? =
        reservationRepository.findByIdForUpdate(reservationId)?.let { it.toSnapshot(reservationItemRepository.findSeatIdsByReservationId(reservationId)) }

    override fun getReservations(userId: Long, page: Int, size: Int): ReservationListResponse {
        val result = reservationRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(page, size))
        return ReservationListResponse(
            items = result.content.map { reservation ->
                reservation.toSnapshot(reservationItemRepository.findSeatIdsByReservationId(reservation.id!!)).toResponse()
            },
            page = result.number,
            size = result.size,
            total = result.totalElements,
        )
    }

    override fun markReservationConfirmed(reservationId: Long) {
        val reservation = reservationRepository.findByIdForUpdate(reservationId) ?: throw NotFoundException("RESERVATION", reservationId)
        reservation.reservationStatus = ReservationStatus.CONFIRMED
    }

    override fun markReservationCanceled(reservationId: Long, canceledAt: LocalDateTime) {
        val reservation = reservationRepository.findByIdForUpdate(reservationId) ?: throw NotFoundException("RESERVATION", reservationId)
        reservation.reservationStatus = ReservationStatus.CANCELED
        reservation.canceledAt = canceledAt
    }

    override fun markReservationExpiredByHold(holdId: String, expiredAt: LocalDateTime) {
        val reservation = reservationRepository.findByHoldIdAndStatus(holdId, ReservationStatus.PENDING_PAYMENT)
        if (reservation != null) {
            reservation.reservationStatus = ReservationStatus.EXPIRED
            reservation.expiredAt = expiredAt
        }
    }

    override fun expirePendingReservations(now: LocalDateTime) {
        reservationRepository.findExpiredPendingReservations(ReservationStatus.PENDING_PAYMENT, now)
            .forEach { reservation ->
                reservation.reservationStatus = ReservationStatus.EXPIRED
                reservation.expiredAt = now
                reservation.hold.holdStatus = HoldStatus.EXPIRED
                val seatIds = reservationItemRepository.findSeatIdsByReservationId(reservation.id!!)
                markSeatsAvailable(seatIds)
            }
    }

    override fun hasSuccessfulPayment(reservationId: Long): Boolean =
        paymentRepository.findTopByReservationIdAndPaymentStatusOrderByIdDesc(reservationId, PaymentStatus.SUCCESS) != null

    override fun createSuccessPayment(reservationId: Long, amount: Long, method: String, paidAt: LocalDateTime): PaymentSnapshot {
        val reservation = reservationRepository.findByIdForUpdate(reservationId) ?: throw NotFoundException("RESERVATION", reservationId)
        val payment = paymentRepository.save(
            PaymentEntity(
                reservation = reservation,
                amount = amount,
                method = PaymentMethod.valueOf(method),
                paymentStatus = PaymentStatus.SUCCESS,
                paidAt = paidAt,
            ),
        )
        return PaymentSnapshot(payment.id!!, reservationId, amount, payment.paymentStatus.name, payment.paidAt!!)
    }

    override fun createFailedPayment(reservationId: Long, amount: Long, method: String, failedAt: LocalDateTime, reason: String): PaymentSnapshot {
        val reservation = reservationRepository.findByIdForUpdate(reservationId) ?: throw NotFoundException("RESERVATION", reservationId)
        val payment = paymentRepository.save(
            PaymentEntity(
                reservation = reservation,
                amount = amount,
                method = runCatching { PaymentMethod.valueOf(method) }.getOrDefault(PaymentMethod.CARD),
                paymentStatus = PaymentStatus.FAILED,
                paidAt = failedAt,
                failureReason = reason,
            ),
        )
        return PaymentSnapshot(payment.id!!, reservationId, amount, payment.paymentStatus.name, payment.paidAt!!)
    }

    override fun cancelSuccessPayment(reservationId: Long, canceledAt: LocalDateTime): Boolean {
        val payment = paymentRepository.findTopByReservationIdAndPaymentStatusOrderByIdDesc(reservationId, PaymentStatus.SUCCESS) ?: return false
        payment.paymentStatus = PaymentStatus.CANCELED
        payment.canceledAt = canceledAt
        return true
    }

    override fun deduct(userId: Long, amount: Long, now: LocalDateTime) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("USER", userId) }
        val point = userPointRepository.findForUpdate(userId) ?: throw NotFoundException("POINT", userId)
        if (point.balance < amount) throw ConflictException("포인트가 부족합니다.", conflictType = "POINT_BALANCE")
        point.balance -= amount
        point.updatedAt = now
        pointTransactionRepository.save(PointTransactionEntity(UUID.randomUUID().toString(), user, amount, PointTransactionType.USE, point.balance, now))
    }

    override fun refund(userId: Long, amount: Long, now: LocalDateTime) {
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("USER", userId) }
        val point = userPointRepository.findForUpdate(userId) ?: throw NotFoundException("POINT", userId)
        point.balance += amount
        point.updatedAt = now
        pointTransactionRepository.save(PointTransactionEntity(UUID.randomUUID().toString(), user, amount, PointTransactionType.REFUND, point.balance, now))
    }

    private fun SeatHoldEntity.toSnapshot(seatIds: List<Long>) = HoldSnapshot(
        holdId = holdId,
        userId = user.id!!,
        concertId = schedule.concert.id!!,
        scheduleId = schedule.id!!,
        queueToken = queueToken.queueToken,
        seatIds = seatIds,
        totalAmount = totalAmount,
        status = holdStatus.name,
        holdExpiresAt = holdExpiresAt,
    )

    private fun ReservationEntity.toSnapshot(seatIds: List<Long>) = ReservationSnapshot(
        reservationId = id!!,
        userId = user.id!!,
        concertId = schedule.concert.id!!,
        scheduleId = schedule.id!!,
        holdId = hold.holdId,
        seatIds = seatIds,
        totalAmount = totalAmount,
        status = reservationStatus.name,
        createdAt = createdAt!!,
    )

    private fun ReservationSnapshot.toResponse() = ReservationResponse(
        reservationId = reservationId,
        userId = userId,
        scheduleId = scheduleId,
        seatIds = seatIds,
        totalAmount = totalAmount,
        status = status,
        createdAt = createdAt,
    )

    private fun evictScheduleCaches(seats: List<kr.hhplus.be.server.domain.entity.SeatEntity>) {
        seats.map { it.schedule }.distinctBy { it.id!! }
            .forEach { schedule ->
                concertCacheService.evictScheduleView(schedule.concert.id!!, schedule.id!!)
            }
    }
}
