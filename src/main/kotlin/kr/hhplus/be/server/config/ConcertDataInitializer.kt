package kr.hhplus.be.server.config

import kr.hhplus.be.server.domain.entity.ConcertEntity
import kr.hhplus.be.server.domain.entity.ConcertScheduleEntity
import kr.hhplus.be.server.domain.entity.SeatEntity
import kr.hhplus.be.server.domain.entity.UserEntity
import kr.hhplus.be.server.domain.entity.UserPointEntity
import kr.hhplus.be.server.domain.enums.ScheduleStatus
import kr.hhplus.be.server.domain.enums.SeatStatus
import kr.hhplus.be.server.domain.repository.ConcertRepository
import kr.hhplus.be.server.domain.repository.ConcertScheduleRepository
import kr.hhplus.be.server.domain.repository.SeatRepository
import kr.hhplus.be.server.domain.repository.UserPointRepository
import kr.hhplus.be.server.domain.repository.UserRepository
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.LocalDateTime

@Configuration
class ConcertDataInitializer {
    @Bean
    fun seedConcertData(
        concertRepository: ConcertRepository,
        concertScheduleRepository: ConcertScheduleRepository,
        seatRepository: SeatRepository,
        userRepository: UserRepository,
        userPointRepository: UserPointRepository,
        clock: Clock,
    ) = ApplicationRunner {
        if (concertRepository.count() == 0L) {
            val concert = concertRepository.save(
                ConcertEntity(
                    title = "2026 HH Plus Concert",
                    venueName = "Olympic Hall",
                    bookingOpenAt = LocalDateTime.of(2026, 3, 1, 10, 0),
                    bookingCloseAt = LocalDateTime.of(2026, 3, 31, 23, 59),
                ),
            )
            val schedules = concertScheduleRepository.saveAll(
                listOf(
                    ConcertScheduleEntity(concert, LocalDateTime.of(2026, 3, 20, 19, 0), LocalDateTime.of(2026, 3, 20, 21, 0), ScheduleStatus.OPEN),
                    ConcertScheduleEntity(concert, LocalDateTime.of(2026, 3, 21, 19, 0), LocalDateTime.of(2026, 3, 21, 21, 0), ScheduleStatus.OPEN),
                ),
            )
            seatRepository.saveAll(
                schedules.flatMap { schedule ->
                    (1..50).map { index ->
                        SeatEntity(
                            schedule = schedule,
                            section = "A",
                            rowLabel = ((index - 1) / 10 + 1).toString(),
                            seatNumber = index.toString(),
                            price = 50_000L,
                            seatStatus = SeatStatus.AVAILABLE,
                        )
                    }
                },
            )
        }
        if (!userRepository.existsByEmail("demo@hhplus.kr")) {
            val user = userRepository.save(UserEntity("demo@hhplus.kr", "demo", "password123"))
            userPointRepository.save(UserPointEntity(user = user, balance = 200_000L, updatedAt = LocalDateTime.now(clock)))
        }
    }
}
