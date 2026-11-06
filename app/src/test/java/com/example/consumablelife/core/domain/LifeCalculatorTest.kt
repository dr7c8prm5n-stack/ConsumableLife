package com.example.consumablelife.core.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** 15.1 LifeCalculator 단위 테스트 (필수 케이스) — FR-C05 */
class LifeCalculatorTest {

    private val today = LocalDate.of(2026, 11, 3)
    private val oil = Interval(km = 15_000, months = 12)

    private fun calc(
        interval: Interval = oil,
        lastOdo: Double = 30_000.0,
        lastDate: LocalDate = today.minusMonths(3),
        now: Double,
        avg: Double? = 40.0,
    ) = LifeCalculator.calculate(LifeInput(now, today, interval, LastReplacement(lastOdo, lastDate), avg))

    @Test fun `1 절반 사용 - distRatio 0_5, timeRatio 약 0_5 → GOOD 50%`() {
        val r = calc(lastDate = today.minusMonths(6), now = 37_500.0)
        assertThat(r.distRatio).isWithin(1e-9).of(0.5)
        assertThat(r.timeRatio!!).isWithin(0.01).of(0.5)
        assertThat(r.status).isEqualTo(Status.GOOD)
        assertThat(r.lifePercent).isWithin(1f).of(50f)
    }

    @Test fun `2 남은 1,400km - 6_1 표 규칙상 ratio 0_093 이므로 DUE`() {
        // 명세 15.1 #2는 CAUTION을 기대하지만 6.1 표(0 ≤ ratio < 0.10 → DUE)와 모순 → 표를 우선. 보고서 참고
        val r = calc(now = 43_600.0)
        assertThat(r.remainingKm).isEqualTo(1_400)
        assertThat(r.status).isEqualTo(Status.DUE)
    }

    @Test fun `2b 절대값 규칙 - 비율은 여유(35%)지만 남은 1,400km 이하 → CAUTION`() {
        val r = calc(interval = Interval(km = 4_000), lastOdo = 30_000.0, now = 32_600.0)
        assertThat(r.remainingRatio!!).isWithin(1e-9).of(0.35)
        assertThat(r.remainingKm).isEqualTo(1_400)
        assertThat(r.status).isEqualTo(Status.CAUTION)
    }

    @Test fun `3 거리 여유 + 기간 11_5개월 경과 → 제한요인 TIME, 남은 약 15일`() {
        val r = calc(lastOdo = 30_000.0, lastDate = today.minusDays(350), now = 31_000.0)
        assertThat(r.limitingFactor).isEqualTo(Factor.TIME)
        assertThat(r.remainingDays).isIn(14..16)
        // 명세 #3은 CAUTION 기대 — timeRatio 0.042 < 0.10 이라 6.1 표 기준 DUE
        assertThat(r.status).isEqualTo(Status.DUE)
    }

    @Test fun `3b 남은 기간 30일 이하 절대값 규칙 → CAUTION`() {
        val r = calc(interval = Interval(months = 2), lastOdo = 30_000.0, lastDate = today.minusDays(32), now = 31_000.0)
        assertThat(r.remainingRatio!!).isAtLeast(0.30) // 비율은 GOOD 구간
        assertThat(r.remainingDays).isAtMost(30)
        assertThat(r.status).isEqualTo(Status.CAUTION)
    }

    @Test fun `4 주기 340km 초과 → OVERDUE, 0%, 초과 340km`() {
        val r = calc(now = 45_340.0)
        assertThat(r.status).isEqualTo(Status.OVERDUE)
        assertThat(r.lifePercent).isEqualTo(0f)
        assertThat(r.remainingKm).isEqualTo(-340)
        assertThat(roundKmForDisplay(-r.remainingKm!!)).isEqualTo(340)
    }

    @Nested inner class `5 한쪽 주기만 존재` {
        @Test fun `거리 주기만 - 기간은 무시`() {
            val r = calc(interval = Interval(km = 100_000), lastDate = today.minusYears(20), now = 40_000.0)
            assertThat(r.timeRatio).isNull()
            assertThat(r.remainingDays).isNull()
            assertThat(r.limitingFactor).isEqualTo(Factor.DISTANCE)
            assertThat(r.status).isEqualTo(Status.GOOD)
        }

        @Test fun `기간 주기만(와이퍼) - 거리는 무시`() {
            val r = calc(interval = Interval(months = 12), lastOdo = 0.0, lastDate = today.minusMonths(3), now = 900_000.0)
            assertThat(r.distRatio).isNull()
            assertThat(r.remainingKm).isNull()
            assertThat(r.limitingFactor).isEqualTo(Factor.TIME)
            assertThat(r.status).isEqualTo(Status.GOOD)
        }
    }

    @Test fun `6 교체 기록 odo가 현재 odo보다 큼 → UNKNOWN + 입력오류`() {
        val r = calc(lastOdo = 50_000.0, now = 48_000.0)
        assertThat(r.status).isEqualTo(Status.UNKNOWN)
        assertThat(r.inputError).isTrue()
    }

    @Test fun `6b 기록 없음 → UNKNOWN (입력오류 아님)`() {
        val r = LifeCalculator.calculate(LifeInput(10_000.0, today, oil, null, null))
        assertThat(r.status).isEqualTo(Status.UNKNOWN)
        assertThat(r.inputError).isFalse()
    }

    @Test fun `7 스냅샷 5일치 → 평균 없음, 예상 교체일은 기간 기준만, 수집 중`() {
        val snaps = (0..4).map { today.minusDays(it.toLong()) to (48_000.0 - it * 50) }
        val avg = LifeCalculator.averageKmPerDay(snaps, today)
        assertThat(avg).isNull()
        val r = calc(now = 37_500.0, avg = avg)
        assertThat(r.collectingData).isTrue()
        assertThat(r.dueDate).isEqualTo(today.plusDays(r.remainingDays!!.toLong()))
    }

    @Test fun `7b 스냅샷 30일 → (최대-최소) 나누기 일수, 거리 기준 예상일 계산`() {
        val snaps = (0..30).map { today.minusDays(it.toLong()) to (48_000.0 - it * 40) }
        val avg = LifeCalculator.averageKmPerDay(snaps, today)
        assertThat(avg).isWithin(1e-9).of(40.0)
        val r = calc(lastDate = today.minusDays(10), now = 43_000.0, avg = avg) // 남은 2,000km → 50일, 기간 355일
        assertThat(r.dueDate).isEqualTo(today.plusDays(50))
        assertThat(r.collectingData).isFalse()
    }

    @Test fun `8 냉각수 최초주기 → 1회 교체 후 4만km 2년으로 전환`() {
        val coolant = ConsumableItem(
            id = "COOLANT", customName = null, iconKey = "coolant",
            normal = Interval(40_000, 24), severe = Interval(40_000, 24), first = Interval(200_000, 120),
            enabled = true, sortOrder = 0, powertrains = Powertrain.entries.toSet(), sensorLink = null, zone = CarZone.ENGINE_BAY,
        )
        assertThat(coolant.activeInterval(Condition.NORMAL, realReplacementCount = 0)).isEqualTo(Interval(200_000, 120))
        assertThat(coolant.activeInterval(Condition.SEVERE, realReplacementCount = 0)).isEqualTo(Interval(200_000, 120))
        assertThat(coolant.activeInterval(Condition.NORMAL, realReplacementCount = 1)).isEqualTo(Interval(40_000, 24))

        val first = calc(interval = Interval(200_000, 120), lastOdo = 0.0, lastDate = today.minusYears(3), now = 48_000.0)
        assertThat(first.status).isEqualTo(Status.GOOD)
        val after = calc(interval = Interval(40_000, 24), lastOdo = 10_000.0, lastDate = today.minusYears(1), now = 48_000.0)
        assertThat(after.status).isEqualTo(Status.DUE) // 38,000/40,000 사용
    }

    @Test fun `9 mile 입력은 km 정규화 후 동일 결과`() {
        val km = calc(lastOdo = 30_000.0, now = 37_500.0)
        val mi = calc(lastOdo = milesToKm(kmToMiles(30_000.0)), now = milesToKm(kmToMiles(37_500.0)))
        assertThat(mi.remainingRatio!!).isWithin(1e-9).of(km.remainingRatio!!)
        assertThat(mi.status).isEqualTo(km.status)
        assertThat(milesToKm(1.0)).isWithin(1e-9).of(1.609344)
    }

    @Test fun `10 임계값 경계 - 0_30 GOOD, 0_10 CAUTION, 0 DUE, 음수 OVERDUE`() {
        val th = Thresholds()
        assertThat(LifeCalculator.judge(0.30, 10_000, 300, th)).isEqualTo(Status.GOOD)
        assertThat(LifeCalculator.judge(0.10, 10_000, 300, th)).isEqualTo(Status.CAUTION)
        assertThat(LifeCalculator.judge(0.0, 10_000, 300, th)).isEqualTo(Status.DUE)
        assertThat(LifeCalculator.judge(-0.0001, 10_000, 300, th)).isEqualTo(Status.OVERDUE)
        // 절대값 경계 (포함)
        assertThat(LifeCalculator.judge(0.5, 1_500, 300, th)).isEqualTo(Status.CAUTION)
        assertThat(LifeCalculator.judge(0.5, 1_501, 300, th)).isEqualTo(Status.GOOD)
        assertThat(LifeCalculator.judge(0.5, null, 30, th)).isEqualTo(Status.CAUTION)
        assertThat(LifeCalculator.judge(0.5, null, 31, th)).isEqualTo(Status.GOOD)
    }

    @Test fun `FR-C04 정렬 - OVERDUE, SENSOR, DUE, CAUTION, GOOD, UNKNOWN 및 ratio 오름차순`() {
        data class X(val s: Status, val r: Double?)
        val list = listOf(
            X(Status.GOOD, 0.8), X(Status.UNKNOWN, null), X(Status.CAUTION, 0.2), X(Status.GOOD, 0.4),
            X(Status.SENSOR_ALERT, 0.9), X(Status.OVERDUE, -0.1), X(Status.DUE, 0.05),
        )
        val sorted = list.sortedForDashboard({ it.s }, { it.r })
        assertThat(sorted.map { it.s }).containsExactly(
            Status.OVERDUE, Status.SENSOR_ALERT, Status.DUE, Status.CAUTION, Status.GOOD, Status.GOOD, Status.UNKNOWN,
        ).inOrder()
        assertThat(sorted[4].r).isEqualTo(0.4)
    }

    @Test fun `FR-C02 센서 경고는 계산 상태를 덮어쓰지 않고 정렬 키로만 사용`() {
        assertThat(effectiveStatus(Status.GOOD, SensorAlert.EngineOilLow)).isEqualTo(Status.SENSOR_ALERT)
        assertThat(effectiveStatus(Status.OVERDUE, SensorAlert.EngineOilLow)).isEqualTo(Status.OVERDUE)
        assertThat(effectiveStatus(Status.CAUTION, null)).isEqualTo(Status.CAUTION)
    }

    @Test fun `UX-14 반올림 - 1,000km 이상 100 단위, 미만 10 단위`() {
        assertThat(roundKmForDisplay(1_234)).isEqualTo(1_200)
        assertThat(roundKmForDisplay(39_050)).isEqualTo(39_100)
        assertThat(roundKmForDisplay(824)).isEqualTo(820)
        assertThat(roundKmForDisplay(345)).isEqualTo(350)
        assertThat(roundKmForDisplay(-340)).isEqualTo(-340)
    }
}
