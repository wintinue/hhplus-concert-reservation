package kr.hhplus.be.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Check

@Entity
@Table(
    name = "seat_hold_items",
    indexes = [
        Index(name = "idx_hold_items_seat", columnList = "seat_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_hold_items_hold_seat", columnNames = ["hold_id", "seat_id"]),
    ],
)
@Check(constraints = "price >= 0")
class SeatHoldItemEntity(
    @ManyToOne(optional = false)
    @JoinColumn(name = "hold_id")
    var hold: SeatHoldEntity,
    @ManyToOne(optional = false)
    @JoinColumn(name = "seat_id")
    var seat: SeatEntity,
    @Column(nullable = false)
    var price: Long,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hold_item_id")
    var id: Long? = null
}
