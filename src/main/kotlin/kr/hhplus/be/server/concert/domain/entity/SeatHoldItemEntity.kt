package kr.hhplus.be.server.concert.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "seat_hold_items")
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
