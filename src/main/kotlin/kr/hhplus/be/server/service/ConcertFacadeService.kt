package kr.hhplus.be.server.service

import kr.hhplus.be.server.api.ConcertListResponse
import kr.hhplus.be.server.api.ConcertSummary
import kr.hhplus.be.server.api.FastSoldOutConcertListResponse
import kr.hhplus.be.server.api.PointBalanceResponse
import kr.hhplus.be.server.api.PointChargeResponse
import kr.hhplus.be.server.api.ScheduleListResponse
import kr.hhplus.be.server.api.ScheduleSummary
import kr.hhplus.be.server.api.SeatListResponse
import kr.hhplus.be.server.api.SeatSummary
import kr.hhplus.be.server.common.cache.ConcertCacheService
import kr.hhplus.be.server.common.lock.DistributedLockExecutor
import kr.hhplus.be.server.common.ranking.ConcertRankingService
import kr.hhplus.be.server.common.NotFoundException
import kr.hhplus.be.server.common.ValidationException
import kr.hhplus.be.server.domain.entity.PointTransactionEntity
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.entity.UserPointEntity
import kr.hhplus.be.server.domain.enums.PointTransactionType
import kr.hhplus.be.server.domain.enums.ScheduleStatus
import kr.hhplus.be.server.domain.enums.SeatStatus
import kr.hhplus.be.server.domain.repository.ConcertRepository
import kr.hhplus.be.server.domain.repository.ConcertScheduleRepository
import kr.hhplus.be.server.domain.repository.PointTransactionRepository
import kr.hhplus.be.server.domain.repository.SeatRepository
import kr.hhplus.be.server.domain.repository.UserPointRepository
import kr.hhplus.be.server.queue.QueueService
import kr.hhplus.be.server.reservation.application.HoldPort
import kr.hhplus.be.server.reservation.application.ReservationPort
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Service
class ConcertFacadeService(
    private val concertRepository: ConcertRepository,
    private val scheduleRepository: ConcertScheduleRepository,
    private val seatRepository: SeatRepository,
    private val userPointRepository: UserPointRepository,
    private val pointTransactionRepository: PointTransactionRepository,
    private val queueService: QueueService,
    private val holdPort: HoldPort,
    private val reservationPort: ReservationPort,
    private val concertCacheService: ConcertCacheService,
    private val concertRankingService: ConcertRankingService,
    private val lockExecutor: DistributedLockExecutor,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun getConcerts(page: Int, size: Int): ConcertListResponse {
        return concertCacheService.getConcerts(page, size) {
            val pageable = PageRequest.of(page, size)
            val result = concertRepository.findAll(pageable)
            ConcertListResponse(
                items = result.content.map {
                    ConcertSummary(it.id!!, it.title, it.venueName, it.bookingOpenAt, it.bookingCloseAt)
                },
                page = result.number,
                size = result.size,
                total = result.totalElements,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getFastSoldOutConcerts(limit: Int): FastSoldOutConcertListResponse =
        concertRankingService.getFastSoldOutConcerts(limit)

    @Transactional
    fun getSchedules(user: UserEntity, concertId: Long, queueToken: String): ScheduleListResponse {
        holdPort.expireActiveHolds(LocalDateTime.now(clock))
        reservationPort.expirePendingReservations(LocalDateTime.now(clock))
        queueService.validateQueueTokenForRead(queueToken, user.id!!, concertId)
        return concertCacheService.getSchedules(concertId) {
            val concert = concertRepository.findById(concertId).orElseThrow { NotFoundException("CONCERT", concertId) }
            val schedules = scheduleRepository.findByConcertIdAndStatusOrderByStartAt(concert.id!!, ScheduleStatus.OPEN)
            ScheduleListResponse(
                items = schedules.map { schedule ->
                    val seats = seatRepository.findByScheduleIdOrderById(schedule.id!!)
                    ScheduleSummary(
                        scheduleId = schedule.id!!,
                        startAt = schedule.startAt,
                        totalSeat = seats.size,
                        availableSeat = seats.count { it.seatStatus == SeatStatus.AVAILABLE },
                    )
                },
            )
        }
    }

    @Transactional
    fun getSeats(user: UserEntity, scheduleId: Long, queueToken: String): SeatListResponse {
        holdPort.expireActiveHolds(LocalDateTime.now(clock))
        reservationPort.expirePendingReservations(LocalDateTime.now(clock))
        val schedule = scheduleRepository.findById(scheduleId).orElseThrow { NotFoundException("SCHEDULE", scheduleId) }
        queueService.validateQueueTokenForRead(queueToken, user.id!!, schedule.concert.id!!)
        return concertCacheService.getSeats(scheduleId) {
            val seats = seatRepository.findByScheduleIdOrderById(scheduleId)
            SeatListResponse(
                items = seats.map {
                    SeatSummary(it.id!!, it.section, it.rowLabel, it.seatNumber, it.price, it.seatStatus.name)
                },
            )
        }
    }

    @Transactional(readOnly = true)
    fun getMyPoints(user: UserEntity): PointBalanceResponse {
        val point = userPointRepository.findById(user.id!!).orElseThrow { NotFoundException("POINT", user.id!!) }
        return PointBalanceResponse(point.userId!!, point.balance, point.updatedAt)
    }

    fun charge(user: UserEntity, amount: Long): PointChargeResponse {
        if (amount <= 0) {
            throw ValidationException("충전 금액은 1 이상이어야 합니다.", listOf(mapOf("field" to "amount", "reason" to "must be greater than 0")))
        }
        return lockExecutor.execute("point:user:${user.id!!}") {
            transactionTemplate.execute {
                val now = LocalDateTime.now(clock)
                val point = userPointRepository.findForUpdate(user.id!!) ?: userPointRepository.save(UserPointEntity(user = user, balance = 0, updatedAt = now))
                point.balance += amount
                point.updatedAt = now
                val tx = pointTransactionRepository.save(
                    PointTransactionEntity(UUID.randomUUID().toString(), user, amount, PointTransactionType.CHARGE, point.balance, now),
                )
                PointChargeResponse(tx.transactionId, user.id!!, amount, point.balance, now)
            } ?: error("point charge transaction returned null")
        }
    }
}
