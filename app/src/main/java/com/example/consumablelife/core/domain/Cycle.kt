package com.example.consumablelife.core.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * 부록 A 2장 — 사용 사이클.
 * "사용 사이클" = 현재 교체 주기 중 얼마나 썼는지(진행률) + 몇 번째 주기인지(회차).
 * 기존 카드의 "남은 수명 %"와 반대 방향 지표이므로 화면에는 반드시 "사용" 라벨을 붙인다.
 */

data class CycleInfo(
    /** 1 - remainingRatio. 0 = 방금 교체, 1 = 교체 시점, >1 = 초과. 계산 불가(UNKNOWN) = null */
    val cycleProgress: Float?,
    /** 거리 기준 초과분 km (텍스트 "+340 km 초과") */
    val overflowKm: Int,
    /** 실제 교체 기록 수 + 1 */
    val cycleIndex: Int,
    /** 추정 기록(온보딩 "모름")으로 시작한 사이클 — DG-02 */
    val estimated: Boolean,
    val limitingFactor: Factor?,
) {
    /** 링은 100%에서 멈춤 */
    val ringFill: Float get() = (cycleProgress ?: 0f).coerceIn(0f, 1f)
}

object CycleCalculator {
    /** LifeCalculator 결과에서 파생 (DG-01: 화면 계층에서 계산하지 않음) */
    fun of(result: LifeResult, interval: Interval, records: List<ReplacementRecord>, odoNowKm: Double): CycleInfo {
        val last = records.maxWithOrNull(compareBy<ReplacementRecord>({ it.date }, { it.odometerKm }))
        val progress = result.remainingRatio
            ?.takeIf { result.status != Status.UNKNOWN }
            ?.let { (1.0 - it).toFloat().coerceAtLeast(0f) }
        val overflow = if (last != null && interval.km != null) max(0, (odoNowKm - (last.odometerKm + interval.km)).roundToInt()) else 0
        val real = records.count { it.source != RecordSource.ESTIMATED }
        return CycleInfo(
            cycleProgress = progress,
            overflowKm = overflow,
            cycleIndex = real + 1,
            estimated = last?.source == RecordSource.ESTIMATED,
            limitingFactor = result.limitingFactor,
        )
    }
}

/** 부록 A 10장 — 사이클 이력 막대 1개 */
data class CycleSpan(
    val index: Int,
    val start: LocalDate,
    val end: LocalDate?,
    /** 그 사이클에 실제 주행한 km (역행 기록이면 null) */
    val km: Double?,
    val months: Double,
    val current: Boolean,
    val estimated: Boolean,
)

data class CycleSummary(val avgKm: Double?, val avgMonths: Double?)

object CycleHistory {
    private const val DAYS_PER_MONTH = 30.4375

    /**
     * 교체 기록(순서 무관) → 회차별 사이클. 각 기록이 한 사이클의 시작이며 마지막 사이클은 진행 중.
     * 실제 기록 k건 뒤의 사이클은 (k+1)회차, 추정 기록으로 시작한 사이클은 1회차(추정).
     * 첫 기록 이전(신차~첫 교체)은 시작 시점을 몰라 표시하지 않는다.
     */
    fun spans(records: List<ReplacementRecord>, odoNowKm: Double, today: LocalDate, maxCount: Int = 6): List<CycleSpan> {
        val sorted = records.sortedWith(compareBy({ it.date }, { it.odometerKm }))
        var real = 0
        val all = sorted.mapIndexed { i, r ->
            if (r.source != RecordSource.ESTIMATED) real++
            val next = sorted.getOrNull(i + 1)
            val endOdo = next?.odometerKm ?: odoNowKm
            val endDate = next?.date ?: today
            CycleSpan(
                index = real + 1,
                start = r.date,
                end = next?.date,
                km = (endOdo - r.odometerKm).takeIf { it >= 0 },
                months = ChronoUnit.DAYS.between(r.date, endDate).coerceAtLeast(0) / DAYS_PER_MONTH,
                current = next == null,
                estimated = r.source == RecordSource.ESTIMATED,
            )
        }
        return all.takeLast(maxCount)
    }

    /** DG-42 평균 교체 간격 — 완료된 사이클만 */
    fun summary(spans: List<CycleSpan>): CycleSummary {
        val done = spans.filter { !it.current }
        if (done.isEmpty()) return CycleSummary(null, null)
        val kms = done.mapNotNull { it.km }
        return CycleSummary(kms.takeIf { it.isNotEmpty() }?.average(), done.map { it.months }.average())
    }
}
