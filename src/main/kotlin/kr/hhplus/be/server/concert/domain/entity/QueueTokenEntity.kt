package kr.hhplus.be.server.concert.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Index
import kr.hhplus.be.server.concert.domain.enums.QueueStatus
import java.time.LocalDateTime

@Entity
@Table(
    name = "queue_tokens",
    indexes = [
        Index(name = "idx_queue_concert_status_number", columnList = "concert_id, queue_status, queue_number"),
        Index(name = "idx_queue_user_concert", columnList = "user_id, concert_id"),
    ],
)
class QueueTokenEntity(
    @Id
    @Column(name = "queue_token", length = 120)
    var queueToken: String,
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    var user: UserEntity,
    @ManyToOne(optional = false)
    @JoinColumn(name = "concert_id")
    var concert: ConcertEntity,
    @Column(name = "queue_number", nullable = false)
    var queueNumber: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "queue_status", nullable = false)
    var queueStatus: QueueStatus,
    @Column(name = "issued_at", nullable = false)
    var issuedAt: LocalDateTime,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,
    @Column(name = "reservation_window_expires_at")
    var reservationWindowExpiresAt: LocalDateTime? = null,
    @Column(name = "refreshed_at", nullable = false)
    var refreshedAt: LocalDateTime,
) : BaseTimeEntity()
