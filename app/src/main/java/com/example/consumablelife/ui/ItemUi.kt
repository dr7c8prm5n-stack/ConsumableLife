package com.example.consumablelife.ui

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.consumablelife.R
import com.example.consumablelife.core.data.ConsumableState
import com.example.consumablelife.core.data.DashboardData
import com.example.consumablelife.core.designsystem.diagram.DiagramSpec
import com.example.consumablelife.core.designsystem.formatNumber
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.DistanceUnit
import com.example.consumablelife.core.domain.Factor
import com.example.consumablelife.core.domain.Interval
import com.example.consumablelife.core.domain.LifeCalculator
import com.example.consumablelife.core.domain.SensorLink
import com.example.consumablelife.core.domain.Status
import com.example.consumablelife.core.domain.WheelPos
import com.example.consumablelife.core.vehicle.ApiState
import com.example.consumablelife.core.vehicle.LevelState
import com.example.consumablelife.core.vehicle.Telltale
import com.example.consumablelife.core.vehicle.VehicleApi
import kotlin.math.roundToInt

/*
 * 설계서 v3 15장 — 세 탭(차량·대시보드·타임라인)이 공유하는 표시 모델.
 * 한 항목 = 한 ItemUi: 어느 탭에서 봐도 같은 색·같은 아이콘·같은 숫자 형식으로 보인다.
 * 숫자는 도메인(LifeCalculator·CycleCalculator) 결과를 문구로 바꾸기만 한다 (DG-01).
 */

/** DB-03 요약 카운터·필터 묶음. UNKNOWN은 어느 묶음에도 들지 않는다 */
enum class Bucket { DUE, CAUTION, GOOD }

/** 필터 세그먼트 (전체 / 교체 / 곧 점검 / 양호) */
enum class StatusFilter(val bucket: Bucket?) { ALL(null), DUE(Bucket.DUE), CAUTION(Bucket.CAUTION), GOOD(Bucket.GOOD) }

/** 품목과 매칭된 차량 실측값 한 줄. warn = 경고 상태(상태색으로 강조) */
@Immutable
data class LiveFact(val label: String, val value: String, val warn: Boolean = false, val linked: Boolean = true)

@Immutable
data class ItemUi(
    val id: String,
    /** 히어로·상세 */
    val name: String,
    /** 카드·칩·타임라인 라벨 */
    val shortName: String,
    val iconKey: String,
    val kind: CycleKind,
    /** 표시 상태 (센서 경고 포함) */
    val status: Status,
    val bucket: Bucket?,
    val statusLabel: String,
    /** REPLACE/USER = 사용 %, INSPECT = 경과 %. UNKNOWN = null */
    val percent: Int?,
    /** "사용" | "경과" */
    val metricLabel: String,
    /** "약 1,800 km" / "340 km 초과" / "교체 기록을 입력하세요" */
    val remainText: String,
    /** "후 교체" / "후 점검" / "남음" / "" */
    val remainSuffix: String,
    /** 칩·타임라인 라벨용 짧은 값: "1,800 km" / "4개월" / "340 km 초과" */
    val remainValue: String,
    /** 남은 비율 0..1 (LifeBar '남은') */
    val remainFraction: Float,
    val remainKm: Int?,
    val remainMonths: Double?,
    /** 타임라인 레인 */
    val limiting: Factor?,
    val dueLabel: String,
    val dueText: String,
    val lastLabel: String,
    val lastText: String,
    val cycleText: String,
    val basisText: String?,
    val kindText: String,
    /** 근거 VERIFY 표시 */
    val needsVerify: Boolean,
    /** '기본값(수정 가능)' 메타 */
    val isUserDefault: Boolean,
    /** DS-02 주기 수정 가능: USER·사용자 항목·VERIFY 항목만. 정기표 항목은 설정에서만 */
    val editable: Boolean,
    val locationText: String?,
    /** "1,800 / 50,000 km" · "4개월 / 36개월" */
    val distLine: String,
    val cycleIndex: Int,
    val estimated: Boolean,
    /** 사용 사이클 박스 우측: "48,213 / 50,000 km" */
    val cycleUsageText: String,
    val historyCount: Int,
    /** 최근 기록 1건: 날짜 / 주행거리·출처 */
    val lastRecord: Pair<String, String>?,
    /** 차량 감지 경고 문구 */
    val sensorText: String?,
    /** 품목과 매칭된 차량 API 값 */
    val live: List<LiveFact>,
    /** "교체" | "점검" */
    val verb: String,
    val description: String,
) {
    val ringFill: Float get() = ((percent ?: 0) / 100f).coerceIn(0f, 1f)
    val percentText: String get() = percent?.let { "$it%" } ?: "?"
    /** 메타 캡션: 주기 종류 · 기본값(수정 가능) · 위치 — 상세에서만 노출 */
    fun meta(userDefaultLabel: String): String =
        listOfNotNull(kindText, userDefaultLabel.takeIf { isUserDefault }, locationText).joinToString(" · ")
}

private fun bucketOf(s: Status): Bucket? = when (s) {
    Status.OVERDUE, Status.SENSOR_ALERT, Status.DUE -> Bucket.DUE
    Status.CAUTION -> Bucket.CAUTION
    Status.GOOD -> Bucket.GOOD
    Status.UNKNOWN -> null
}

/** DB-02 정렬 우선순위: OVERDUE > (차량 감지) > DUE > CAUTION > UNKNOWN > GOOD */
private fun rank(s: Status): Int = when (s) {
    Status.OVERDUE -> 0
    Status.SENSOR_ALERT -> 1
    Status.DUE -> 2
    Status.CAUTION -> 3
    Status.UNKNOWN -> 4
    Status.GOOD -> 5
}

/** DB-02: 상태 → percent 내림차순 → remainKm 오름차순 */
fun List<ItemUi>.sortedByUrgency(): List<ItemUi> =
    sortedWith(compareBy<ItemUi> { rank(it.status) }.thenByDescending { it.percent ?: -1 }.thenBy { it.remainKm ?: Int.MAX_VALUE })

fun intervalLine(ctx: Context, i: Interval, unit: DistanceUnit): String {
    val parts = listOfNotNull(
        i.km?.let { distance(ctx, it.toDouble(), unit, round = false) },
        i.months?.let { ctx.getString(R.string.fmt_months, it) },
    )
    return if (parts.isEmpty()) ctx.getString(R.string.interval_none) else parts.joinToString(" / ")
}

fun itemUisOf(ctx: Context, d: DashboardData, spec: DiagramSpec): List<ItemUi> =
    d.items.map { itemUiOf(ctx, it, d, spec) }.sortedByUrgency()

private fun itemUiOf(ctx: Context, s: ConsumableState, d: DashboardData, spec: DiagramSpec): ItemUi {
    val unit = d.settings.distanceUnit
    val r = s.result
    val status = s.displayStatus
    val v = verb(ctx, s.kind)
    val name = itemName(ctx, s.item)
    val hotspot = spec.hotspot(s.item.id)
    val pct = s.cycle.cycleProgress?.let { (it * 100).toInt() }
    val months = r.remainingDays?.let { it / LifeCalculator.DAYS_PER_MONTH }

    // 남은 값: 제한 요인 쪽 한 가지만 (목록·칩·타임라인 공통 형식, UX-14 반올림)
    val byTime = r.limitingFactor == Factor.TIME
    val remainText: String
    val remainSuffix: String
    val remainValue: String
    when {
        r.inputError -> { remainText = ctx.getString(R.string.remaining_input_error); remainSuffix = ""; remainValue = statusLabel(ctx, status, s.kind) }
        r.status == Status.UNKNOWN -> { remainText = ctx.getString(R.string.remaining_record_needed, v); remainSuffix = ""; remainValue = statusLabel(ctx, status, s.kind) }
        r.status == Status.OVERDUE -> {
            remainText = if (!byTime && s.cycle.overflowKm > 0) distance(ctx, s.cycle.overflowKm.toDouble(), unit) else duration(ctx, r.remainingDays ?: 0)
            remainSuffix = ctx.getString(R.string.suffix_over)
            remainValue = "$remainText $remainSuffix"
        }
        else -> {
            val value = (if (byTime) r.remainingDays?.let { duration(ctx, it) } else r.remainingKm?.let { distance(ctx, it.toDouble(), unit) }) ?: "—"
            remainText = ctx.getString(R.string.fmt_about, value)
            remainSuffix = if (r.status == Status.GOOD) ctx.getString(R.string.suffix_left) else ctx.getString(R.string.suffix_until, v)
            remainValue = value
        }
    }
    val distLine = when {
        r.status == Status.UNKNOWN -> "—"
        byTime -> listOfNotNull(r.remainingDays?.let { duration(ctx, it.coerceAtLeast(0)) }, s.interval.months?.let { ctx.getString(R.string.fmt_months, it) }).joinToString(" / ")
        else -> listOfNotNull(r.remainingKm?.let { formatNumber(displayKm(it.coerceAtLeast(0), unit, round = true)) }, s.interval.km?.let { distance(ctx, it.toDouble(), unit, round = false) }).joinToString(" / ")
    }
    val cycleUsage = when {
        s.lastRecord == null -> "—"
        s.interval.km != null && !byTime ->
            formatNumber(displayKm((d.odometer.km - s.lastRecord.odometerKm).roundToInt().coerceAtLeast(0), unit, round = false)) + " / " + distance(ctx, s.interval.km.toDouble(), unit, round = false)
        else -> distLine
    }
    val basisWord = when (r.limitingFactor) {
        Factor.DISTANCE -> if (r.collectingData && s.interval.months != null) ctx.getString(R.string.detail_basis_time) else ctx.getString(R.string.detail_basis_distance)
        Factor.TIME -> ctx.getString(R.string.detail_basis_time)
        null -> null
    }
    val lastText = s.lastRecord?.let {
        date(it.date) + " · " + distance(ctx, it.odometerKm, unit, round = false) + if (s.estimated) " · " + ctx.getString(R.string.estimated) else ""
    } ?: "—"

    return ItemUi(
        id = s.item.id, name = name, shortName = if (s.item.isCustom) name else hotspot?.shortLabel ?: name,
        iconKey = s.item.iconKey, kind = s.kind, status = status, bucket = bucketOf(status),
        statusLabel = statusLabel(ctx, status, s.kind),
        percent = pct, metricLabel = usageLabel(ctx, s.kind),
        remainText = remainText, remainSuffix = remainSuffix, remainValue = remainValue,
        remainFraction = (r.remainingRatio ?: 0.0).toFloat().coerceIn(0f, 1f),
        remainKm = r.remainingKm, remainMonths = months, limiting = r.limitingFactor,
        dueLabel = ctx.getString(R.string.detail_due_date, v),
        dueText = r.dueDate?.let { "${date(it)} ($basisWord)" } ?: "—",
        lastLabel = ctx.getString(R.string.detail_last, v), lastText = lastText,
        cycleText = (if (s.usesFirstInterval) ctx.getString(R.string.detail_interval_first) else conditionLabel(ctx, d.settings.drivingCondition)) +
            " · " + intervalLine(ctx, s.interval, unit),
        basisText = hotspot?.source, kindText = kindLabel(ctx, s.kind),
        needsVerify = s.item.needsConfirm, isUserDefault = s.item.isUserDefault && s.kind == CycleKind.USER,
        editable = s.kind == CycleKind.USER || s.item.isCustom || s.item.needsConfirm,
        locationText = hotspot?.anchor?.let { ctx.getString(R.string.car_location, zoneLabel(ctx, s.item.zone)) },
        distLine = distLine, cycleIndex = s.cycle.cycleIndex, estimated = s.cycle.estimated, cycleUsageText = cycleUsage,
        historyCount = s.records.size,
        lastRecord = s.lastRecord?.let { date(it.date) to (distance(ctx, it.odometerKm, unit, round = false) + " · " + sourceLabel(ctx, it.source)) },
        sensorText = s.sensorAlert?.let { ctx.getString(R.string.alert_card, alertLabel(ctx, it)) },
        live = liveFactsOf(ctx, s, d),
        verb = v,
        description = listOf(name, statusLabel(ctx, status, s.kind), "$remainText $remainSuffix".trim()).joinToString(", "),
    )
}

private fun displayKm(km: Int, unit: DistanceUnit, round: Boolean): Int {
    val v = if (unit == DistanceUnit.MILE) com.example.consumablelife.core.domain.kmToMiles(km.toDouble()).roundToInt() else km
    return if (round) com.example.consumablelife.core.domain.roundKmForDisplay(v) else v
}

private fun levelText(ctx: Context, l: LevelState): String = ctx.getString(
    when (l) {
        LevelState.NORMAL -> R.string.live_level_normal
        LevelState.LOW -> R.string.live_level_low
        LevelState.CRITICALLY_LOW -> R.string.live_level_critical
        LevelState.HIGH -> R.string.live_level_high
        LevelState.ERROR -> R.string.live_level_error
        LevelState.UNSUPPORTED -> R.string.live_unsupported
    },
)

private fun wheelShort(ctx: Context, w: WheelPos): String = ctx.getString(
    when (w) {
        WheelPos.FRONT_LEFT -> R.string.wheel_short_FL
        WheelPos.FRONT_RIGHT -> R.string.wheel_short_FR
        WheelPos.REAR_LEFT -> R.string.wheel_short_RL
        WheelPos.REAR_RIGHT -> R.string.wheel_short_RR
    },
)

/**
 * 품목 ↔ 차량 API 매칭 값. 연동되는 API가 없는 품목은 "주행거리·기간으로 계산"임을 그대로 알려 준다.
 * (연동 결과는 설정 > 차량 연동 상태에서 API별로 확인)
 */
fun liveFactsOf(ctx: Context, s: ConsumableState, d: DashboardData): List<LiveFact> {
    val live = d.live
    val caps = d.capabilities
    fun unsupported(label: Int, api: VehicleApi): LiveFact {
        val st = d.apiStatus[api]?.state
        val text = if (st == ApiState.PERMISSION_DENIED) R.string.live_no_permission else R.string.live_unsupported
        return LiveFact(ctx.getString(label), ctx.getString(text), linked = false)
    }
    return when (s.item.sensorLink) {
        SensorLink.BRAKE_FLUID_LEVEL -> listOf(
            if (caps.brakeFluidLevel && d.fluid.brakeFluid != LevelState.UNSUPPORTED)
                LiveFact(ctx.getString(R.string.live_brake_fluid), levelText(ctx, d.fluid.brakeFluid), warn = d.fluid.brakeFluid != LevelState.NORMAL)
            else unsupported(R.string.live_brake_fluid, VehicleApi.BRAKE_FLUID),
        )
        SensorLink.ATF_LEVEL -> listOf(
            if (caps.transmissionOilLevel && d.fluid.transmissionOil != LevelState.UNSUPPORTED)
                LiveFact(ctx.getString(R.string.live_gear_oil), levelText(ctx, d.fluid.transmissionOil), warn = d.fluid.transmissionOil != LevelState.NORMAL)
            else unsupported(R.string.live_gear_oil, VehicleApi.TRANSMISSION_OIL),
        )
        SensorLink.ENGINE_OIL_LEVEL -> listOf(
            if (caps.engineOilLevel && d.fluid.engineOil != LevelState.UNSUPPORTED)
                LiveFact(ctx.getString(R.string.live_engine_oil), levelText(ctx, d.fluid.engineOil), warn = d.fluid.engineOil != LevelState.NORMAL)
            else unsupported(R.string.live_engine_oil, VehicleApi.ENGINE_OIL),
        )
        SensorLink.WASHER -> listOf(
            if (caps.washerFluid)
                LiveFact(ctx.getString(R.string.live_washer), ctx.getString(if (d.fluid.washerLow) R.string.live_washer_low else R.string.live_level_normal), warn = d.fluid.washerLow)
            else unsupported(R.string.live_washer, VehicleApi.WASHER),
        )
        SensorLink.TIRE -> buildList {
            if (live.tirePressureKpa.isNotEmpty()) {
                val text = WheelPos.entries.mapNotNull { w -> live.tirePressureKpa[w]?.let { "${wheelShort(ctx, w)} ${it.roundToInt()}" } }.joinToString(" · ")
                add(LiveFact(ctx.getString(R.string.live_tire_pressure), ctx.getString(R.string.fmt_kpa, text), warn = live.lowTires.any { it.value }))
            } else add(unsupported(R.string.live_tire_pressure, VehicleApi.TIRE_PRESSURE))
            live.tireLowThresholdKpa.values.minOrNull()?.let {
                add(LiveFact(ctx.getString(R.string.live_tire_threshold), ctx.getString(R.string.fmt_kpa, it.roundToInt().toString())))
            }
            if (live.telltales != null) {
                val on = live.telltaleOn(Telltale.TPMS)
                add(LiveFact(ctx.getString(R.string.live_tpms), ctx.getString(if (on) R.string.live_lamp_on else R.string.live_lamp_off), warn = on))
            }
        }
        SensorLink.EV_POWER -> buildList {
            if (live.evSocPercent != null) add(LiveFact(ctx.getString(R.string.live_soc), "${live.evSocPercent}%"))
            else add(unsupported(R.string.live_soc, VehicleApi.EV_SOC))
            if (live.powerDownWarning != null) {
                add(LiveFact(ctx.getString(R.string.live_power_down), ctx.getString(if (live.powerDownWarning) R.string.live_detected else R.string.live_none), warn = live.powerDownWarning))
            } else add(unsupported(R.string.live_power_down, VehicleApi.POWER_DOWN))
        }
        SensorLink.LOW_VOLT_BATTERY -> listOf(
            if (live.telltales != null) {
                val on = live.telltaleOn(Telltale.LOW_VOLT_BATTERY)
                LiveFact(ctx.getString(R.string.live_12v_lamp), ctx.getString(if (on) R.string.live_lamp_on else R.string.live_lamp_off), warn = on)
            } else unsupported(R.string.live_12v_lamp, VehicleApi.TELLTALES),
        )
        null -> listOf(LiveFact(ctx.getString(R.string.live_link), ctx.getString(R.string.live_no_api), linked = false))
    }
}
