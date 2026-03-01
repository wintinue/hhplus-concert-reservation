package kr.hhplus.be.server.concert.domain.repository

import kr.hhplus.be.server.concert.domain.entity.ConcertEntity
import kr.hhplus.be.server.concert.domain.entity.ConcertScheduleEntity
import kr.hhplus.be.server.concert.domain.entity.PaymentEntity
import kr.hhplus.be.server.concert.domain.entity.PointTransactionEntity
import kr.hhplus.be.server.concert.domain.entity.QueueTokenEntity
import kr.hhplus.be.server.concert.domain.entity.ReservationEntity
import kr.hhplus.be.server.concert.domain.entity.ReservationItemEntity
import kr.hhplus.be.server.concert.domain.entity.SeatEntity
import kr.hhplus.be.server.concert.domain.entity.SeatHoldEntity
import kr.hhplus.be.server.concert.domain.entity.SeatHoldItemEntity
import kr.hhplus.be.server.concert.domain.entity.UserEntity
import kr.hhplus.be.server.concert.domain.entity.UserPointEntity
import kr.hhplus.be.server.concert.domain.entity.UserSessionEntity
import kr.hhplus.be.server.concert.domain.enums.HoldStatus
import kr.hhplus.be.server.concert.domain.enums.PaymentStatus
import kr.hhplus.be.server.concert.domain.enums.QueueStatus
import kr.hhplus.be.server.concert.domain.enums.ReservationStatus
import kr.hhplus.be.server.concert.domain.enums.SeatStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.time.LocalDateTime

interface UserRepository : JpaRepository<UserEntity, Long> {
    fun findByEmail(email: String): UserEntity?
    fun existsByEmail(email: String): Boolean
}

interface UserSessionRepository : JpaRepository<UserSessionEntity, String> {
    @Query("select s from UserSessionEntity s join fetch s.user where s.accessToken = :accessToken")
    fun findActiveSession(@Param("accessToken") accessToken: String): UserSessionEntity?
}

interface ConcertRepository : JpaRepository<ConcertEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConcertEntity c where c.id = :concertId")
    fun findByIdForUpdate(@Param("concertId") concertId: Long): ConcertEntity?
}

interface ConcertScheduleRepository : JpaRepository<ConcertScheduleEntity, Long> {
    fun findByConcertIdAndStatusOrderByStartAt(concertId: Long, status: kr.hhplus.be.server.concert.domain.enums.ScheduleStatus): List<ConcertScheduleEntity>
}

interface SeatRepository : JpaRepository<SeatEntity, Long> {
    fun findByScheduleIdOrderById(scheduleId: Long): List<SeatEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeatEntity s where s.id in :seatIds")
    fun findAllForUpdate(@Param("seatIds") seatIds: List<Long>): List<SeatEntity>
}

interface QueueTokenRepository : JpaRepository<QueueTokenEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QueueTokenEntity q where q.user.id = :userId and q.concert.id = :concertId and q.queueStatus in :statuses")
    fun findActiveToken(userId: Long, concertId: Long, statuses: Collection<QueueStatus>): QueueTokenEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select coalesce(max(q.queueNumber), 0) from QueueTokenEntity q where q.concert.id = :concertId")
    fun findMaxQueueNumber(@Param("concertId") concertId: Long): Long

    @Query("select q from QueueTokenEntity q where q.concert.id = :concertId and q.queueStatus = :status order by q.queueNumber asc")
    fun findByConcertAndStatusOrderByQueueNumber(concertId: Long, status: QueueStatus): List<QueueTokenEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QueueTokenEntity q where q.queueToken = :queueToken")
    fun findByIdForUpdate(@Param("queueToken") queueToken: String): QueueTokenEntity?
}

interface SeatHoldRepository : JpaRepository<SeatHoldEntity, String>

interface SeatHoldItemRepository : JpaRepository<SeatHoldItemEntity, Long> {
    fun findByHoldHoldId(holdId: String): List<SeatHoldItemEntity>
}

interface ReservationRepository : JpaRepository<ReservationEntity, Long> {
    fun findByUserIdOrderByIdDesc(userId: Long, pageable: Pageable): Page<ReservationEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationEntity r where r.id = :reservationId")
    fun findByIdForUpdate(@Param("reservationId") reservationId: Long): ReservationEntity?

    fun existsByHoldHoldId(holdId: String): Boolean
}

interface ReservationItemRepository : JpaRepository<ReservationItemEntity, Long> {
    fun findByReservationId(reservationId: Long): List<ReservationItemEntity>
}

interface UserPointRepository : JpaRepository<UserPointEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserPointEntity p where p.userId = :userId")
    fun findForUpdate(@Param("userId") userId: Long): UserPointEntity?
}

interface PointTransactionRepository : JpaRepository<PointTransactionEntity, String>

interface PaymentRepository : JpaRepository<PaymentEntity, Long> {
    fun findTopByReservationIdAndPaymentStatusOrderByIdDesc(reservationId: Long, paymentStatus: PaymentStatus): PaymentEntity?
}
