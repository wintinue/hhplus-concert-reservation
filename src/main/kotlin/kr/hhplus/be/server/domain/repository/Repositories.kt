package kr.hhplus.be.server.domain.repository

import kr.hhplus.be.server.domain.entity.BookingSagaEntity
import kr.hhplus.be.server.domain.entity.ConcertEntity
import kr.hhplus.be.server.domain.entity.ConcertScheduleEntity
import kr.hhplus.be.server.domain.entity.OutboxEventEntity
import kr.hhplus.be.server.domain.entity.PaymentEntity
import kr.hhplus.be.server.domain.entity.PointTransactionEntity
import kr.hhplus.be.server.domain.entity.QueueTokenEntity
import kr.hhplus.be.server.domain.entity.ReservationEntity
import kr.hhplus.be.server.domain.entity.ReservationItemEntity
import kr.hhplus.be.server.domain.entity.SeatEntity
import kr.hhplus.be.server.domain.entity.SeatHoldEntity
import kr.hhplus.be.server.domain.entity.SeatHoldItemEntity
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.entity.UserPointEntity
import kr.hhplus.be.server.domain.entity.UserSessionEntity
import kr.hhplus.be.server.domain.enums.BookingSagaStatus
import kr.hhplus.be.server.domain.enums.HoldStatus
import kr.hhplus.be.server.domain.enums.OutboxEventStatus
import kr.hhplus.be.server.domain.enums.PaymentStatus
import kr.hhplus.be.server.domain.enums.QueueStatus
import kr.hhplus.be.server.domain.enums.ReservationStatus
import kr.hhplus.be.server.domain.enums.SeatStatus
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
    fun findByConcertIdAndStatusOrderByStartAt(concertId: Long, status: kr.hhplus.be.server.domain.enums.ScheduleStatus): List<ConcertScheduleEntity>
}

interface SeatRepository : JpaRepository<SeatEntity, Long> {
    fun findByScheduleIdOrderById(scheduleId: Long): List<SeatEntity>
    fun existsByScheduleIdAndSeatStatusNot(scheduleId: Long, seatStatus: SeatStatus): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SeatEntity s where s.id in :seatIds order by s.id asc")
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
{
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from SeatHoldEntity h where h.holdId = :holdId")
    fun findByIdForUpdate(@Param("holdId") holdId: String): SeatHoldEntity?

    @Query("select h from SeatHoldEntity h where h.holdStatus = :status and h.holdExpiresAt < :now")
    fun findExpiredHolds(@Param("status") status: HoldStatus, @Param("now") now: LocalDateTime): List<SeatHoldEntity>
}

interface SeatHoldItemRepository : JpaRepository<SeatHoldItemEntity, Long> {
    fun findByHoldHoldId(holdId: String): List<SeatHoldItemEntity>

    @Query("select i.seat.id from SeatHoldItemEntity i where i.hold.holdId = :holdId")
    fun findSeatIdsByHoldId(@Param("holdId") holdId: String): List<Long>
}

interface ReservationRepository : JpaRepository<ReservationEntity, Long> {
    fun findByUserIdOrderByIdDesc(userId: Long, pageable: Pageable): Page<ReservationEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservationEntity r where r.id = :reservationId")
    fun findByIdForUpdate(@Param("reservationId") reservationId: Long): ReservationEntity?

    fun existsByHoldHoldId(holdId: String): Boolean

    @Query("select r from ReservationEntity r where r.hold.holdId = :holdId and r.reservationStatus = :status")
    fun findByHoldIdAndStatus(@Param("holdId") holdId: String, @Param("status") status: ReservationStatus): ReservationEntity?

    @Query("select r from ReservationEntity r where r.reservationStatus = :status and r.hold.holdExpiresAt < :now")
    fun findExpiredPendingReservations(@Param("status") status: ReservationStatus, @Param("now") now: LocalDateTime): List<ReservationEntity>
}

interface ReservationItemRepository : JpaRepository<ReservationItemEntity, Long> {
    fun findByReservationId(reservationId: Long): List<ReservationItemEntity>

    @Query("select i.seat.id from ReservationItemEntity i where i.reservation.id = :reservationId")
    fun findSeatIdsByReservationId(@Param("reservationId") reservationId: Long): List<Long>
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

interface OutboxEventRepository : JpaRepository<OutboxEventEntity, Long> {
    fun findByEventKey(eventKey: String): OutboxEventEntity?

    @Query(
        """
        select e from OutboxEventEntity e
        where e.eventStatus in :statuses
        order by e.createdAt asc
        """,
    )
    fun findTop100ByEventStatusInOrderByCreatedAtAsc(@Param("statuses") statuses: Collection<OutboxEventStatus>): List<OutboxEventEntity>
}

interface BookingSagaRepository : JpaRepository<BookingSagaEntity, Long> {
    fun findBySagaId(sagaId: String): BookingSagaEntity?
    fun findByReservationId(reservationId: Long): BookingSagaEntity?

    @Query("select count(b) > 0 from BookingSagaEntity b where b.sagaStatus = :status")
    fun existsBySagaStatus(@Param("status") status: BookingSagaStatus): Boolean
}
