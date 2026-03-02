package kr.hhplus.be.server

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

@SpringBootTest(classes = [ServerApplication::class])
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "local.integration.enabled", matches = "true")
class LocalReservationIntegrationTest : AbstractReservationIntegrationScenarioTest()
