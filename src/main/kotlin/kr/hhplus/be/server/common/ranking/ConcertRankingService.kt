package kr.hhplus.be.server.common.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import kr.hhplus.be.server.api.FastSoldOutConcertListResponse
import kr.hhplus.be.server.api.FastSoldOutConcertSummary
import kr.hhplus.be.server.domain.entity.ConcertScheduleEntity
import kr.hhplus.be.server.domain.repository.ConcertRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Component
class ConcertRankingService(
    private val redisTemplate: StringRedisTemplate,
    private val concertRepository: ConcertRepository,
    private val objectMapper: ObjectMapper,
) {
    fun recordFastSoldOut(schedule: ConcertScheduleEntity, soldOutAt: LocalDateTime) {
        val concert = schedule.concert
        val concertId = concert.id ?: return
        val elapsedMillis = Duration.between(concert.bookingOpenAt, soldOutAt).toMillis().coerceAtLeast(0)
        val score = -elapsedMillis.toDouble()
        val member = concertId.toString()
        val currentScore = redisTemplate.opsForZSet().score(FAST_SOLD_OUT_RANKING_KEY, member)

        if (currentScore == null || score > currentScore) {
            redisTemplate.opsForZSet().add(FAST_SOLD_OUT_RANKING_KEY, member, score)
            redisTemplate.opsForValue().set(
                metadataKey(concertId),
                objectMapper.writeValueAsString(
                    FastSoldOutRankingMetadata(
                        scheduleId = schedule.id!!,
                        soldOutAt = soldOutAt,
                        soldOutSeconds = elapsedMillis / 1000,
                    ),
                ),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getFastSoldOutConcerts(limit: Int): FastSoldOutConcertListResponse {
        if (limit <= 0) return FastSoldOutConcertListResponse(emptyList())

        val rankedConcertIds = redisTemplate.opsForZSet()
            .reverseRange(FAST_SOLD_OUT_RANKING_KEY, 0, limit.toLong() - 1)
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()

        if (rankedConcertIds.isEmpty()) {
            return FastSoldOutConcertListResponse(emptyList())
        }

        val concertsById = concertRepository.findAllById(rankedConcertIds).associateBy { it.id!! }
        val items = rankedConcertIds.mapIndexedNotNull { index, concertId ->
            val concert = concertsById[concertId] ?: return@mapIndexedNotNull null
            val metadata = redisTemplate.opsForValue().get(metadataKey(concertId))
                ?.let { objectMapper.readValue(it, FastSoldOutRankingMetadata::class.java) }
                ?: return@mapIndexedNotNull null

            FastSoldOutConcertSummary(
                rank = index + 1,
                concertId = concertId,
                scheduleId = metadata.scheduleId,
                title = concert.title,
                venueName = concert.venueName,
                bookingOpenAt = concert.bookingOpenAt,
                soldOutAt = metadata.soldOutAt,
                soldOutSeconds = metadata.soldOutSeconds,
            )
        }

        return FastSoldOutConcertListResponse(items)
    }

    fun clearRanking() {
        val keys = redisTemplate.keys("$FAST_SOLD_OUT_META_PREFIX*")
        if (!keys.isNullOrEmpty()) {
            redisTemplate.delete(keys)
        }
        redisTemplate.delete(FAST_SOLD_OUT_RANKING_KEY)
    }

    private fun metadataKey(concertId: Long): String = "$FAST_SOLD_OUT_META_PREFIX$concertId"

    companion object {
        const val FAST_SOLD_OUT_RANKING_KEY = "concert:ranking:fast-sold-out"
        private const val FAST_SOLD_OUT_META_PREFIX = "concert:ranking:fast-sold-out:meta:"
    }
}

data class FastSoldOutRankingMetadata(
    val scheduleId: Long,
    val soldOutAt: LocalDateTime,
    val soldOutSeconds: Long,
)
