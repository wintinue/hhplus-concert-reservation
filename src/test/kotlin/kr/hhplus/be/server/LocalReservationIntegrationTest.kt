package kr.hhplus.be.server

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [ServerApplication::class])
@ActiveProfiles("test")
class LocalReservationIntegrationTest : AbstractReservationIntegrationScenarioTest()
