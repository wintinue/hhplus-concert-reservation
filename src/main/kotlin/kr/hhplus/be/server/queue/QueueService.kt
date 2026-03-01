package kr.hhplus.be.server.queue

import kr.hhplus.be.server.api.QueuePositionResponse
import kr.hhplus.be.server.api.QueueTokenIssueResponse
import kr.hhplus.be.server.common.ConflictException
import kr.hhplus.be.server.common.NotFoundException
import kr.hhplus.be.server.common.QueueExpiredException
import kr.hhplus.be.server.common.QueueNotReadyException
import kr.hhplus.be.server.domain.entity.ConcertEntity
import kr.hhplus.be.server.domain.entity.QueueTokenEntity
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.enums.QueueStatus
import kr.hhplus.be.server.domain.repository.ConcertRepository
import kr.hhplus.be.server.domain.repository.QueueTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class QueueService(
    private val concertRepository: ConcertRepository,
    private val queueTokenRepository: QueueTokenRepository,
    private val clock: Clock,
) {
    // TODO: 현재는 MySQL + 애플리케이션 체크 + 락으로 활성 queue token 중복을 방지한다.
    // 후속 고도화 시 active_slot 기반 유니크 또는 활성/이력 테이블 분리를 먼저 검토하고,
    // Redis 대기열 전환은 그 다음 단계에서 고려한다.
    @Transactional
    fun issueToken(user: UserEntity, concertId: Long): QueueTokenIssueResponse {
        val concert = concertRepository.findByIdForUpdate(concertId) ?: throw NotFoundException("CONCERT", concertId)
        val active = queueTokenRepository.findActiveToken(user.id!!, concertId, activeStatuses())
        if (active != null) {
            throw ConflictException(
                "이미 유효한 대기열 토큰이 있습니다.",
                conflictType = "QUEUE_ALREADY_ISSUED",
                extra = mapOf("activeQueueToken" to active.queueToken),
            )
        }
        val now = LocalDateTime.now(clock)
        val nextNumber = queueTokenRepository.findMaxQueueNumber(concertId) + 1
        val admitted = queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(concertId, QueueStatus.ADMITTED).isEmpty()
        val token = QueueTokenEntity(
            queueToken = UUID.randomUUID().toString(),
            user = user,
            concert = concert,
            queueNumber = nextNumber,
            queueStatus = if (admitted) QueueStatus.ADMITTED else QueueStatus.WAITING,
            issuedAt = now,
            expiresAt = now.plusMinutes(30),
            reservationWindowExpiresAt = if (admitted) now.plusMinutes(10) else null,
            refreshedAt = now,
        )
        queueTokenRepository.save(token)
        return token.toIssueResponse()
    }

    @Transactional
    fun getPosition(userId: Long, queueToken: String): QueuePositionResponse {
        val now = LocalDateTime.now(clock)
        val token = queueTokenRepository.findById(queueToken).orElseThrow { NotFoundException("QUEUE_TOKEN", queueToken) }
        refreshStatuses(token.concert)
        val current = queueTokenRepository.findById(queueToken).orElseThrow { NotFoundException("QUEUE_TOKEN", queueToken) }
        validateTokenOwnership(current, userId)
        if (current.queueStatus == QueueStatus.EXPIRED) {
            throw QueueExpiredException("대기열 토큰이 만료되었습니다.", current.queueStatus.name, current.expiresAt)
        }
        val waiting = queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(current.concert.id!!, QueueStatus.WAITING)
        val aheadCount = waiting.count { it.queueNumber < current.queueNumber }.toLong()
        val currentPosition = if (current.queueStatus == QueueStatus.WAITING) aheadCount + 1 else 1
        val remaining = if (current.queueStatus == QueueStatus.WAITING) aheadCount * 30 else 0
        current.refreshedAt = now
        return queueTokenRepository.save(current).toPositionResponse(currentPosition, aheadCount, waiting.size.toLong(), remaining)
    }

    @Transactional
    fun validateQueueTokenForRead(queueToken: String, userId: Long, concertId: Long? = null): QueueTokenEntity {
        val token = queueTokenRepository.findById(queueToken).orElseThrow { NotFoundException("QUEUE_TOKEN", queueToken) }
        refreshStatuses(token.concert)
        validateTokenOwnership(token, userId)
        validateConcertMatch(token, concertId)
        validateTokenStatus(token)
        return token
    }

    @Transactional
    fun validateReservableToken(queueToken: String, userId: Long, concertId: Long? = null): QueueTokenEntity {
        val token = queueTokenRepository.findByIdForUpdate(queueToken) ?: throw NotFoundException("QUEUE_TOKEN", queueToken)
        refreshStatuses(token.concert)
        validateTokenOwnership(token, userId)
        validateConcertMatch(token, concertId)
        validateTokenStatus(token)
        if (token.queueStatus == QueueStatus.ADMITTED) {
            token.queueStatus = QueueStatus.IN_PROGRESS
        }
        return queueTokenRepository.save(token)
    }

    @Transactional
    fun expireAfterPayment(queueToken: QueueTokenEntity) {
        val now = LocalDateTime.now(clock)
        queueToken.queueStatus = QueueStatus.EXPIRED
        queueToken.expiresAt = now
        queueToken.reservationWindowExpiresAt = now
        queueTokenRepository.save(queueToken)
    }

    @Transactional
    fun expireAfterPayment(queueToken: String) {
        val entity = queueTokenRepository.findByIdForUpdate(queueToken) ?: throw NotFoundException("QUEUE_TOKEN", queueToken)
        expireAfterPayment(entity)
    }

    private fun validateTokenOwnership(token: QueueTokenEntity, userId: Long) {
        if (token.user.id != userId) {
            throw QueueNotReadyException("대기열 토큰 사용자 정보가 일치하지 않습니다.", token.queueStatus.name, 1)
        }
    }

    private fun validateConcertMatch(token: QueueTokenEntity, concertId: Long?) {
        if (concertId != null && token.concert.id != concertId) {
            throw QueueNotReadyException("다른 콘서트의 대기열 토큰입니다.", token.queueStatus.name, 1)
        }
    }

    private fun validateTokenStatus(token: QueueTokenEntity) {
        if (token.expiresAt.isBefore(LocalDateTime.now(clock)) || token.queueStatus == QueueStatus.EXPIRED) {
            throw QueueExpiredException("대기열 토큰이 만료되었습니다.", token.queueStatus.name, token.expiresAt)
        }
        if (token.queueStatus != QueueStatus.ADMITTED && token.queueStatus != QueueStatus.IN_PROGRESS) {
            throw QueueNotReadyException("아직 예약 가능한 순번이 아닙니다.", token.queueStatus.name, 30)
        }
    }

    private fun refreshStatuses(concert: ConcertEntity) {
        concertRepository.findByIdForUpdate(concert.id!!) ?: throw NotFoundException("CONCERT", concert.id!!)
        val now = LocalDateTime.now(clock)
        val admitted = queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(concert.id!!, QueueStatus.ADMITTED)
        admitted.filter { it.reservationWindowExpiresAt != null && it.reservationWindowExpiresAt!!.isBefore(now) }.forEach {
            it.queueStatus = QueueStatus.EXPIRED
            queueTokenRepository.save(it)
        }
        val activeAdmitted = queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(concert.id!!, QueueStatus.ADMITTED)
        if (activeAdmitted.isEmpty()) {
            val waiting = queueTokenRepository.findByConcertAndStatusOrderByQueueNumber(concert.id!!, QueueStatus.WAITING)
            val next = waiting.firstOrNull()
            if (next != null) {
                next.queueStatus = QueueStatus.ADMITTED
                next.reservationWindowExpiresAt = now.plusMinutes(10)
                queueTokenRepository.save(next)
            }
        }
    }

    private fun QueueTokenEntity.toIssueResponse() = QueueTokenIssueResponse(
        queueToken = queueToken,
        concertId = concert.id!!,
        queueNumber = queueNumber,
        queueStatus = queueStatus.name,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        pollingIntervalSeconds = 30,
    )

    private fun QueueTokenEntity.toPositionResponse(
        currentPosition: Long,
        aheadCount: Long,
        totalWaitingCount: Long,
        remainingWaitSeconds: Long,
    ) = QueuePositionResponse(
        queueToken = queueToken,
        concertId = concert.id!!,
        queueNumber = queueNumber,
        currentPosition = currentPosition,
        aheadCount = aheadCount,
        totalWaitingCount = totalWaitingCount,
        estimatedWaitSeconds = remainingWaitSeconds,
        remainingWaitSeconds = remainingWaitSeconds,
        queueStatus = queueStatus.name,
        canReserve = queueStatus == QueueStatus.ADMITTED || queueStatus == QueueStatus.IN_PROGRESS,
        tokenExpiresAt = expiresAt,
        tokenRemainingSeconds = maxOf(0, ChronoUnit.SECONDS.between(LocalDateTime.now(clock), expiresAt)),
        reservationWindowExpiresAt = reservationWindowExpiresAt,
        reservationWindowRemainingSeconds = maxOf(0, ChronoUnit.SECONDS.between(LocalDateTime.now(clock), reservationWindowExpiresAt ?: LocalDateTime.now(clock))),
        refreshedAt = refreshedAt,
    )

    private fun activeStatuses() = listOf(QueueStatus.WAITING, QueueStatus.ADMITTED, QueueStatus.IN_PROGRESS)
}
