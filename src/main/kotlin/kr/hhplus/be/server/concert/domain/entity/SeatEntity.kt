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
import kr.hhplus.be.server.concert.domain.enums.SeatStatus

@Entity
@Table(
    name = "seats",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_seats_schedule_position", columnNames = ["schedule_id", "section", "row_label", "seat_number"]),
    ],
)
class SeatEntity(
    @ManyToOne(optional = false)
    @JoinColumn(name = "schedule_id")
    var schedule: ConcertScheduleEntity,
    @Column(nullable = false)
    var section: String,
    @Column(name = "row_label", nullable = false)
    var rowLabel: String,
    @Column(name = "seat_number", nullable = false)
    var seatNumber: String,
    @Column(nullable = false)
    var price: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "seat_status", nullable = false)
    var seatStatus: SeatStatus,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    var id: Long? = null
}
