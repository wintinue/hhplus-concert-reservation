package kr.hhplus.be.server.domain.entity

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
import kr.hhplus.be.server.domain.enums.ScheduleStatus
import java.time.LocalDateTime

@Entity
@Table(name = "concert_schedules")
class ConcertScheduleEntity(
    @ManyToOne(optional = false)
    @JoinColumn(name = "concert_id")
    var concert: ConcertEntity,
    @Column(name = "start_at", nullable = false)
    var startAt: LocalDateTime,
    @Column(name = "end_at", nullable = false)
    var endAt: LocalDateTime,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ScheduleStatus,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    var id: Long? = null
}
