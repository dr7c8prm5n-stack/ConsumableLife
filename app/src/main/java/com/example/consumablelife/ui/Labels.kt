package com.example.consumablelife.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.consumablelife.R
import com.example.consumablelife.core.data.ConsumableState
import com.example.consumablelife.core.designsystem.formatNumber
import com.example.consumablelife.core.domain.CarZone
import com.example.consumablelife.core.domain.Condition
import com.example.consumablelife.core.domain.ConsumableItem
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.DistanceUnit
import com.example.consumablelife.core.domain.Factor
import com.example.consumablelife.core.domain.Powertrain
import com.example.consumablelife.core.domain.RecordSource
import com.example.consumablelife.core.domain.SensorAlert
import com.example.consumablelife.core.domain.Status
import com.example.consumablelife.core.domain.WheelPos
import com.example.consumablelife.core.domain.kmToMiles
import com.example.consumablelife.core.domain.roundKmForDisplay
import com.example.consumablelife.core.vehicle.ApiState
import com.example.consumablelife.core.vehicle.VehicleApi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/* 도메인 → 문구 변환 (NFR-06: 문자열은 strings.xml) */

fun itemName(ctx: Context, item: ConsumableItem): String = item.customName ?: when (item.id) {
    "BATTERY_12V" -> ctx.getString(R.string.item_BATTERY_12V)
    "WIPER" -> ctx.getString(R.string.item_WIPER)
    "CABIN_FILTER" -> ctx.getString(R.string.item_CABIN_FILTER)
    "HV_BATTERY" -> ctx.getString(R.string.item_HV_BATTERY)
    "TIRE_ROTATION" -> ctx.getString(R.string.item_TIRE_ROTATION)
    "COOLANT" -> ctx.getString(R.string.item_COOLANT)
    "BRAKE_PAD" -> ctx.getString(R.string.item_BRAKE_PAD)
    "BRAKE_FLUID" -> ctx.getString(R.string.item_BRAKE_FLUID)
    "REDUCER_OIL" -> ctx.getString(R.string.item_REDUCER_OIL)
    "TIRE_REPLACE" -> ctx.getString(R.string.item_TIRE_REPLACE)
    else -> ctx.getString(R.string.item_custom_fallback)
}

/** DG2-I1: INSPECT 품목은 "교체" 대신 "점검" */
fun verb(ctx: Context, kind: CycleKind): String =
    ctx.getString(if (kind == CycleKind.INSPECT) R.string.verb_inspect else R.string.verb_replace)

fun kindLabel(ctx: Context, kind: CycleKind): String = ctx.getString(
    when (kind) {
        CycleKind.REPLACE -> R.string.kind_REPLACE
        CycleKind.INSPECT -> R.string.kind_INSPECT
        CycleKind.USER -> R.string.kind_USER
    },
)

fun statusLabel(ctx: Context, s: Status, kind: CycleKind = CycleKind.REPLACE): String = ctx.getString(
    when (s) {
        Status.GOOD -> R.string.status_GOOD
        Status.CAUTION -> if (kind == CycleKind.INSPECT) R.string.status_CAUTION_inspect else R.string.status_CAUTION
        Status.DUE -> if (kind == CycleKind.INSPECT) R.string.status_DUE_inspect else R.string.status_DUE
        Status.OVERDUE -> R.string.status_OVERDUE
        Status.SENSOR_ALERT -> R.string.status_SENSOR_ALERT
        Status.UNKNOWN -> R.string.status_UNKNOWN
    },
)

fun zoneLabel(ctx: Context, z: CarZone): String = ctx.getString(
    when (z) {
        CarZone.ENGINE_BAY -> R.string.zone_ENGINE_BAY
        CarZone.WINDSHIELD -> R.string.zone_WINDSHIELD
        CarZone.CABIN -> R.string.zone_CABIN
        CarZone.UNDERBODY -> R.string.zone_UNDERBODY
        CarZone.DRIVETRAIN -> R.string.zone_DRIVETRAIN
        CarZone.WHEELS -> R.string.zone_WHEELS
        CarZone.OTHER -> R.string.zone_OTHER
    },
)

fun wheelLabel(ctx: Context, w: WheelPos): String = ctx.getString(
    when (w) {
        WheelPos.FRONT_LEFT -> R.string.wheel_FRONT_LEFT
        WheelPos.FRONT_RIGHT -> R.string.wheel_FRONT_RIGHT
        WheelPos.REAR_LEFT -> R.string.wheel_REAR_LEFT
        WheelPos.REAR_RIGHT -> R.string.wheel_REAR_RIGHT
    },
)

fun alertLabel(ctx: Context, a: SensorAlert): String = when (a) {
    SensorAlert.EngineOilLow -> ctx.getString(R.string.alert_ENGINE_OIL)
    SensorAlert.BrakeFluidLow -> ctx.getString(R.string.alert_BRAKE_FLUID)
    SensorAlert.TransmissionOilLow -> ctx.getString(R.string.alert_ATF)
    SensorAlert.WasherFluidLow -> ctx.getString(R.string.alert_WASHER)
    is SensorAlert.TirePressureLow -> ctx.getString(R.string.alert_TIRE) +
        if (a.wheels.isEmpty()) "" else " (" + a.wheels.joinToString { wheelLabel(ctx, it) } + ")"
    SensorAlert.EvPowerLimited -> ctx.getString(R.string.alert_EV_POWER)
    SensorAlert.LowVoltBattery -> ctx.getString(R.string.alert_12V)
}

fun conditionLabel(ctx: Context, c: Condition) =
    ctx.getString(if (c == Condition.SEVERE) R.string.cond_SEVERE else R.string.cond_NORMAL)

fun powertrainLabel(ctx: Context, p: Powertrain) = ctx.getString(
    when (p) { Powertrain.ICE -> R.string.pt_ICE; Powertrain.HEV -> R.string.pt_HEV; Powertrain.EV -> R.string.pt_EV },
)

fun sourceLabel(ctx: Context, s: RecordSource) = ctx.getString(
    when (s) {
        RecordSource.VEHICLE_ODO -> R.string.source_VEHICLE_ODO
        RecordSource.MANUAL -> R.string.source_MANUAL
        RecordSource.ESTIMATED -> R.string.source_ESTIMATED
    },
)

/** km 값 → 표시 단위 문자열. round=true면 UX-14 반올림 */
fun distance(ctx: Context, km: Double, unit: DistanceUnit, round: Boolean = true): String {
    val v = if (unit == DistanceUnit.MILE) kmToMiles(km) else km
    val n = if (round) roundKmForDisplay(v.roundToInt()) else v.roundToInt()
    return ctx.getString(if (unit == DistanceUnit.MILE) R.string.fmt_mi else R.string.fmt_km, formatNumber(n))
}

/** 일수 → 일/주/개월/년 (2년 이상은 소수 1자리 년 — 냉각수 최초 주기 "7.6년") */
fun duration(ctx: Context, days: Int): String {
    val d = abs(days)
    return when {
        d < 14 -> ctx.getString(R.string.fmt_days, d)
        d < 60 -> ctx.getString(R.string.fmt_weeks, (d / 7.0).roundToInt())
        d < 730 -> ctx.getString(R.string.fmt_months, (d / 30.4375).roundToInt())
        else -> ctx.getString(R.string.fmt_years_decimal, String.format(Locale.KOREA, "%.1f", d / 365.25))
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
fun date(d: LocalDate): String = d.format(dateFmt)

/** 카드 보조 문구 1줄 (UX-14) */
fun remainingLine(ctx: Context, s: ConsumableState, unit: DistanceUnit): String {
    val r = s.result
    if (r.inputError) return ctx.getString(R.string.remaining_input_error)
    if (r.status == Status.UNKNOWN) return ctx.getString(R.string.remaining_record_needed, verb(ctx, s.kind))
    val km = r.remainingKm
    val days = r.remainingDays
    if (r.status == Status.OVERDUE) {
        return if (r.limitingFactor == Factor.DISTANCE && km != null) ctx.getString(R.string.fmt_over, distance(ctx, -km.toDouble(), unit))
        else ctx.getString(R.string.fmt_over, duration(ctx, days ?: 0))
    }
    val distText = km?.let { distance(ctx, it.toDouble(), unit) }
    val timeText = days?.let { duration(ctx, it) }
    return when {
        distText != null && timeText != null && r.status != Status.GOOD -> ctx.getString(R.string.fmt_about, "$distText · $timeText")
        r.limitingFactor == Factor.TIME && timeText != null -> ctx.getString(R.string.fmt_about, timeText)
        distText != null -> ctx.getString(R.string.fmt_about, distText)
        timeText != null -> ctx.getString(R.string.fmt_about, timeText)
        else -> ""
    }
}

/** UX-02 히어로 문구: "와이퍼 블레이드 · 약 820km 또는 3주 후 교체" (최대 2줄). INSPECT는 "후 점검" */
fun heroLine(ctx: Context, s: ConsumableState, unit: DistanceUnit): String {
    val name = itemName(ctx, s.item)
    val v = verb(ctx, s.kind)
    val r = s.result
    if (r.status == Status.UNKNOWN) return ctx.getString(R.string.dash_hero_unknown, name, v)
    if (r.status == Status.OVERDUE) return ctx.getString(R.string.dash_hero_over, name, remainingLine(ctx, s, unit), v)
    val distText = r.remainingKm?.let { distance(ctx, it.toDouble(), unit) }
    val timeText = r.remainingDays?.let { duration(ctx, it) }
    val about = when {
        distText != null && timeText != null -> ctx.getString(R.string.fmt_about_or, distText, timeText)
        distText != null -> ctx.getString(R.string.fmt_about, distText)
        else -> ctx.getString(R.string.fmt_about, timeText ?: "")
    }
    return if (r.status == Status.GOOD) ctx.getString(R.string.dash_hero_good, name, about)
    else ctx.getString(R.string.dash_hero_due, name, about, v)
}

/** 설정 > 차량 연동 상태: API가 어떤 품목·값에 쓰이는지 */
fun apiLabel(ctx: Context, api: VehicleApi): String = ctx.getString(
    when (api) {
        VehicleApi.ODOMETER -> R.string.api_ODOMETER
        VehicleApi.TRIP_DISTANCE -> R.string.api_TRIP_DISTANCE
        VehicleApi.TRIP_TIME -> R.string.api_TRIP_TIME
        VehicleApi.BRAKE_FLUID -> R.string.api_BRAKE_FLUID
        VehicleApi.TRANSMISSION_OIL -> R.string.api_TRANSMISSION_OIL
        VehicleApi.ENGINE_OIL -> R.string.api_ENGINE_OIL
        VehicleApi.WASHER -> R.string.api_WASHER
        VehicleApi.POWER_DOWN -> R.string.api_POWER_DOWN
        VehicleApi.EV_SOC -> R.string.api_EV_SOC
        VehicleApi.TIRE_THRESHOLD -> R.string.api_TIRE_THRESHOLD
        VehicleApi.TIRE_PRESSURE -> R.string.api_TIRE_PRESSURE
        VehicleApi.TELLTALES -> R.string.api_TELLTALES
    },
)

fun apiStateLabel(ctx: Context, s: ApiState?): String = ctx.getString(
    when (s) {
        ApiState.CONNECTED -> R.string.api_state_CONNECTED
        ApiState.NO_VALUE -> R.string.api_state_NO_VALUE
        ApiState.UNSUPPORTED -> R.string.api_state_UNSUPPORTED
        ApiState.PERMISSION_DENIED -> R.string.api_state_PERMISSION_DENIED
        ApiState.FAILED -> R.string.api_state_FAILED
        null -> R.string.api_state_PENDING
    },
)

@Composable
fun rememberCtx(): Context = LocalContext.current
