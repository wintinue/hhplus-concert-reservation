package kr.hhplus.be.server.concert.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import kr.hhplus.be.server.concert.domain.enums.ReservationStatus
import java.time.LocalDateTime

@Entity
@Table(
    name = "reservations",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_reservations_hold_id", columnNames = ["hold_id"]),
    ],
)
class ReservationEntity(
    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    var user: UserEntity,
    @ManyToOne(optional = false)
    @JoinColumn(name = "schedule_id")
    var schedule: ConcertScheduleEntity,
    @ManyToOne(optional = false)
    @JoinColumn(name = "hold_id")
    var hold: SeatHoldEntity,
    @Column(name = "total_amount", nullable = false)
    var totalAmount: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_status", nullable = false)
    var reservationStatus: ReservationStatus,
    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = null,
    @Column(name = "expired_at")
    var expiredAt: LocalDateTime? = null,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    var id: Long? = null
}
