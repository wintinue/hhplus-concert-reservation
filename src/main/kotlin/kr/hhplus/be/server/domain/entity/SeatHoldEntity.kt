package kr.hhplus.be.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kr.hhplus.be.server.domain.enums.HoldStatus
import org.hibernate.annotations.Check
import java.time.LocalDateTime

@Entity
@Table(
    name = "seat_holds",
    indexes = [
        Index(name = "idx_holds_expiry", columnList = "hold_expires_at"),
    ],
)
@Check(constraints = "total_amount >= 0")
class SeatHoldEntity(
    @Id
    @Column(name = "hold_id", length = 120)
    var holdId: String,
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    var user: UserEntity,
    @ManyToOne(optional = false)
    @JoinColumn(name = "schedule_id")
    var schedule: ConcertScheduleEntity,
    @ManyToOne(optional = false)
    @JoinColumn(name = "queue_token")
    var queueToken: QueueTokenEntity,
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_status", nullable = false)
    var holdStatus: HoldStatus,
    @Column(name = "total_amount", nullable = false)
    var totalAmount: Long,
    @Column(name = "hold_expires_at", nullable = false)
    var holdExpiresAt: LocalDateTime,
) : BaseTimeEntity()
