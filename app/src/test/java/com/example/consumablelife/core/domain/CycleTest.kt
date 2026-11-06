package com.example.consumablelife.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** 부록 A 2장 사용 사이클 · 10장 이력 */
class CycleTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val oil = Interval(km = 15_000, months = 12)
    private fun rec(id: Long, date: LocalDate, odo: Double, src: RecordSource = RecordSource.MANUAL) =
        ReplacementRecord(id, "ENGINE_OIL", odo, date, src, null)

    @Test fun `초과 340km - cycleProgress 1_023, overflow 340, 3회차`() {
        val recs = listOf(rec(1, today.minusMonths(15), 15_000.0), rec(2, today.minusMonths(3), 30_000.0))
        val r = LifeCalculator.calculate(LifeInput(45_340.0, today, oil, LastReplacement(30_000.0, today.minusMonths(3)), 40.0))
        val c = CycleCalculator.of(r, oil, recs, 45_340.0)
        assertThat(c.cycleProgress!!).isWithin(1e-3f).of(1.0227f)
        assertThat(c.ringFill).isEqualTo(1f)
        assertThat(c.overflowKm).isEqualTo(340)
        assertThat(c.cycleIndex).isEqualTo(3)
        assertThat(c.estimated).isFalse()
        assertThat(c.limitingFactor).isEqualTo(Factor.DISTANCE)
    }

    @Test fun `DG-02 추정 기록으로 시작한 사이클 - 1회차(추정)`() {
        val recs = listOf(rec(1, today.minusMonths(6), 40_713.0, RecordSource.ESTIMATED))
        val r = LifeCalculator.calculate(LifeInput(48_213.0, today, oil, LastReplacement(40_713.0, today.minusMonths(6)), 40.0))
        val c = CycleCalculator.of(r, oil, recs, 48_213.0)
        assertThat(c.cycleIndex).isEqualTo(1)
        assertThat(c.estimated).isTrue()
        assertThat(c.cycleProgress!!).isWithin(0.01f).of(0.5f)
    }

    @Test fun `기록 없음 - 진행률 null, 링 0, 1회차`() {
        val r = LifeCalculator.calculate(LifeInput(10_000.0, today, oil, null, null))
        val c = CycleCalculator.of(r, oil, emptyList(), 10_000.0)
        assertThat(c.cycleProgress).isNull()
        assertThat(c.ringFill).isEqualTo(0f)
        assertThat(c.cycleIndex).isEqualTo(1)
    }

    @Test fun `DG-40 이력 - 회차별 실제 주행 km, 마지막은 진행 중, 평균 간격`() {
        val recs = listOf(
            rec(3, LocalDate.of(2026, 3, 10), 40_768.0),
            rec(1, LocalDate.of(2024, 4, 1), 10_000.0),
            rec(2, LocalDate.of(2025, 3, 15), 24_775.0),
        )
        val spans = CycleHistory.spans(recs, odoNowKm = 56_108.0, today = today)
        // 실제 기록 k건째에서 시작한 사이클 = (k+1)회차 (첫 기록 이전 사이클이 1회차, 시작 시점을 몰라 표시하지 않음)
        assertThat(spans.map { it.index }).containsExactly(2, 3, 4).inOrder()
        assertThat(spans.map { it.km }).containsExactly(14_775.0, 15_993.0, 15_340.0).inOrder()
        assertThat(spans.map { it.current }).containsExactly(false, false, true).inOrder()
        assertThat(spans[0].months).isWithin(0.2).of(11.4)
        val sum = CycleHistory.summary(spans)
        assertThat(sum.avgKm!!).isWithin(1e-9).of(15_384.0)
        assertThat(sum.avgMonths!!).isWithin(0.2).of(11.6)
    }

    @Test fun `이력 - 추정 기록은 1회차(추정), 최근 6회차까지만`() {
        val recs = listOf(rec(0, LocalDate.of(2020, 1, 1), 0.0, RecordSource.ESTIMATED)) +
            (1..8).map { rec(it.toLong(), LocalDate.of(2020 + it / 2, 1 + (it % 2) * 6, 1), it * 10_000.0) }
        val spans = CycleHistory.spans(recs, 100_000.0, today)
        assertThat(spans).hasSize(6)
        assertThat(spans.last().index).isEqualTo(9)
        val all = CycleHistory.spans(recs, 100_000.0, today, maxCount = 100)
        assertThat(all.first().estimated).isTrue()
        assertThat(all.first().index).isEqualTo(1)
        assertThat(all[1].index).isEqualTo(2)
        assertThat(CycleHistory.summary(emptyList()).avgKm).isNull()
    }
}
