package kr.hhplus.be.server.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "concerts")
class ConcertEntity(
    @Column(nullable = false)
    var title: String,
    @Column(name = "venue_name", nullable = false)
    var venueName: String,
    @Column(name = "booking_open_at", nullable = false)
    var bookingOpenAt: LocalDateTime,
    @Column(name = "booking_close_at", nullable = false)
    var bookingCloseAt: LocalDateTime,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "concert_id")
    var id: Long? = null
}
