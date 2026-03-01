package kr.hhplus.be.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "reservation_items",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_reservation_items_seat_id", columnNames = ["seat_id"]),
    ],
)
class ReservationItemEntity(
    @ManyToOne(optional = false)
    @JoinColumn(name = "reservation_id")
    var reservation: ReservationEntity,
    @ManyToOne(optional = false)
    @JoinColumn(name = "seat_id")
    var seat: SeatEntity,
    @Column(nullable = false)
    var price: Long,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_item_id")
    var id: Long? = null
}
