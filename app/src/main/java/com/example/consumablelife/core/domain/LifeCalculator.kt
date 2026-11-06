package com.example.consumablelife.core.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

data class LastReplacement(val odoKm: Double, val date: LocalDate)

data class LifeInput(
    val odoNowKm: Double,
    val today: LocalDate,
    val interval: Interval,
    val last: LastReplacement?,
    /** 최근 30일 평균 주행거리. null = 데이터 수집 중 (FR-C01) */
    val avgKmPerDay: Double?,
    val thresholds: Thresholds = Thresholds(),
)

data class LifeResult(
    val status: Status,
    /** min(distRatio, timeRatio). 음수 가능(초과). 계산 불가 시 null */
    val remainingRatio: Double?,
    /** 게이지 표시값 0..100 */
    val lifePercent: Float,
    val remainingKm: Int?,
    val remainingDays: Int?,
    val dueDate: LocalDate?,
    val limitingFactor: Factor?,
    /** 거리 기준 비율 (상세 화면 거리 바) */
    val distRatio: Double?,
    /** 기간 기준 비율 (상세 화면 기간 바) */
    val timeRatio: Double?,
    /** FR-C03 Odometer 역행 등 입력 오류 */
    val inputError: Boolean = false,
    /** FR-C01 주행 데이터 7일 미만 → 거리 기준 예상일 미표시 */
    val collectingData: Boolean = false,
)

/**
 * 소모품 수명 계산기 (6장). 순수 함수 — Android 의존성 없음 (FR-C05).
 */
object LifeCalculator {

    const val DAYS_PER_MONTH = 30.4375
    const val MIN_AVG_KM_PER_DAY = 1.0

    fun calculate(input: LifeInput): LifeResult {
        val last = input.last
        val interval = input.interval
        if (last == null || interval.isEmpty) return unknown()

        // FR-C03: 교체 기록 odo > 현재 odo → 입력 오류
        if (last.odoKm > input.odoNowKm + ODO_TOLERANCE_KM || last.date.isAfter(input.today)) {
            return unknown(inputError = true)
        }

        val usedKm = input.odoNowKm - last.odoKm
        val elapsedDays = ChronoUnit.DAYS.between(last.date, input.today).toDouble()

        val distRatio = interval.km?.let { 1.0 - usedKm / it }
        val timeRatio = interval.months?.let { 1.0 - elapsedDays / (it * DAYS_PER_MONTH) }

        // FR-I01 먼저 도래하는 쪽 기준
        val d = distRatio ?: Double.POSITIVE_INFINITY
        val t = timeRatio ?: Double.POSITIVE_INFINITY
        val remainingRatio = minOf(d, t)
        val limitingFactor = if (d <= t) Factor.DISTANCE else Factor.TIME

        val remainingKm = interval.km?.let { (last.odoKm + it - input.odoNowKm).roundToInt() }
        val remainingDays = interval.months?.let {
            ChronoUnit.DAYS.between(input.today, last.date.plusMonths(it.toLong())).toInt()
        }

        val collecting = input.avgKmPerDay == null
        val daysByDist = if (remainingKm != null && input.avgKmPerDay != null && input.avgKmPerDay >= MIN_AVG_KM_PER_DAY) {
            ceil(remainingKm / input.avgKmPerDay).toInt()
        } else null
        val dueInDays = listOfNotNull(daysByDist, remainingDays).minOrNull()
        val dueDate = dueInDays?.let { input.today.plusDays(it.toLong()) }

        val status = judge(remainingRatio, remainingKm, remainingDays, input.thresholds)

        return LifeResult(
            status = status,
            remainingRatio = remainingRatio,
            lifePercent = (remainingRatio.coerceIn(0.0, 1.0) * 100).toFloat(),
            remainingKm = remainingKm,
            remainingDays = remainingDays,
            dueDate = dueDate,
            limitingFactor = limitingFactor,
            distRatio = distRatio,
            timeRatio = timeRatio,
            collectingData = collecting,
        )
    }

    /**
     * 6.1 상태 판정. 경계 포함 규칙 (15.1 #10):
     * ratio 0.30 → GOOD, 0.10 → CAUTION, 0 → DUE, <0 → OVERDUE
     */
    fun judge(ratio: Double, remainingKm: Int?, remainingDays: Int?, th: Thresholds): Status = when {
        ratio < 0.0 -> Status.OVERDUE
        ratio < th.dueRatio -> Status.DUE
        ratio < th.cautionRatio -> Status.CAUTION
        remainingKm != null && remainingKm <= th.cautionKm -> Status.CAUTION
        remainingDays != null && remainingDays <= th.cautionDays -> Status.CAUTION
        else -> Status.GOOD
    }

    /**
     * FR-C01 평균 주행거리: 최근 30일 스냅샷의 (최대-최소)/일수.
     * 데이터 기간 7일 미만이면 null (수집 중).
     */
    fun averageKmPerDay(snapshots: List<Pair<LocalDate, Double>>, today: LocalDate): Double? {
        val window = snapshots.filter { !it.first.isBefore(today.minusDays(30)) && !it.first.isAfter(today) }
        if (window.size < 2) return null
        val first = window.minBy { it.first }
        val last = window.maxBy { it.first }
        val spanDays = ChronoUnit.DAYS.between(first.first, last.first)
        if (spanDays < MIN_SNAPSHOT_SPAN_DAYS) return null
        val km = window.maxOf { it.second } - window.minOf { it.second }
        return (km / spanDays).coerceAtLeast(0.0)
    }

    private fun unknown(inputError: Boolean = false) = LifeResult(
        status = Status.UNKNOWN, remainingRatio = null, lifePercent = 0f,
        remainingKm = null, remainingDays = null, dueDate = null, limitingFactor = null,
        distRatio = null, timeRatio = null, inputError = inputError,
    )

    private const val ODO_TOLERANCE_KM = 0.5
    const val MIN_SNAPSHOT_SPAN_DAYS = 7
}

/** 대시보드에서의 실효 상태: 센서 경고는 OVERDUE 다음 순위 (FR-C04) */
fun effectiveStatus(status: Status, sensorAlert: SensorAlert?): Status =
    if (sensorAlert != null && status != Status.OVERDUE) Status.SENSOR_ALERT else status

/** FR-C04 정렬: 상태 순 → 같은 상태 내 remainingRatio 오름차순 */
fun <T> List<T>.sortedForDashboard(status: (T) -> Status, ratio: (T) -> Double?): List<T> =
    sortedWith(compareBy<T> { status(it).ordinal }.thenBy { ratio(it) ?: Double.MAX_VALUE })

/** UX-14 숫자 반올림: 1,000km 이상은 100km 단위, 미만은 10km 단위 */
fun roundKmForDisplay(km: Int): Int {
    val abs = kotlin.math.abs(km)
    val step = if (abs >= 1000) 100 else 10
    val rounded = ((abs + step / 2) / step) * step
    return if (km < 0) -rounded else rounded
}

const val KM_PER_MILE = 1.609344

fun milesToKm(mi: Double): Double = mi * KM_PER_MILE
fun kmToMiles(km: Double): Double = km / KM_PER_MILE
