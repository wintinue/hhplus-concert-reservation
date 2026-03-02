package kr.hhplus.be.server

import kr.hhplus.be.server.common.ranking.ConcertRankingService
import kr.hhplus.be.server.domain.enums.ScheduleStatus
import kr.hhplus.be.server.domain.enums.SeatStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers(disabledWithoutDocker = true)
class ConcertRankingIntegrationTest : AbstractReservationIntegrationScenarioTest() {
    @Autowired
    private lateinit var concertRankingService: ConcertRankingService

    @BeforeEach
    fun clearRanking() {
        concertRankingService.clearRanking()
    }

    @Test
    fun `마지막 좌석 결제 시 빠른 매진 랭킹이 생성된다`() {
        val user = createUser()
        val concert = concertRepository.findAll().first()
        val schedule = scheduleRepository.findByConcertIdAndStatusOrderByStartAt(concert.id!!, ScheduleStatus.OPEN).first()
        val seats = seatRepository.findByScheduleIdOrderById(schedule.id!!)
        val targetSeat = seats.first()

        seats.drop(1).forEach { it.seatStatus = SeatStatus.SOLD }
        seatRepository.saveAllAndFlush(seats.drop(1))

        val queueToken = createAdmittedToken(user, concert.id!!, 1L)
        concertFacadeService.charge(user, targetSeat.price)
        val hold = holdSeatsUseCase.execute(user.id!!, queueToken, schedule.id!!, listOf(targetSeat.id!!))
        val reservation = createReservationUseCase.execute(user.id!!, queueToken, hold.holdId)
        payReservationUseCase.execute(user.id!!, queueToken, reservation.reservationId, reservation.totalAmount, "CARD")

        val ranking = concertFacadeService.getFastSoldOutConcerts(10)

        assertEquals(1, ranking.items.size)
        assertEquals(concert.id!!, ranking.items.first().concertId)
        assertEquals(schedule.id!!, ranking.items.first().scheduleId)
        assertEquals(1, ranking.items.first().rank)
        assertEquals(0, seatRepository.findByScheduleIdOrderById(schedule.id!!).count { it.seatStatus != SeatStatus.SOLD })
    }

    companion object {
        @Container
        @JvmStatic
        val mySqlContainer: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("hhplus")
            .withUsername("test")
            .withPassword("test")

        @Container
        @JvmStatic
        val redisContainer: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mySqlContainer.getJdbcUrl() + "?characterEncoding=UTF-8&serverTimezone=UTC" }
            registry.add("spring.datasource.username") { mySqlContainer.username }
            registry.add("spring.datasource.password") { mySqlContainer.password }
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379) }
        }
    }
}
