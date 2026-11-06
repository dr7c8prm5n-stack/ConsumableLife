package com.example.consumablelife.ui

import android.content.Context
import com.example.consumablelife.R
import com.example.consumablelife.core.data.ConsumableState
import com.example.consumablelife.core.designsystem.diagram.CycleUi
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.DistanceUnit
import com.example.consumablelife.core.domain.Factor
import com.example.consumablelife.core.domain.Status
import kotlin.math.roundToInt

/*
 * 명세 v2.0 — 도메인(ConsumableState.cycle, CycleCalculator)에서 계산된 사용 사이클 값을 콜아웃·라이프 루프 문구로 변환.
 * 숫자는 여기서 계산하지 않는다 (DG-01).
 */

/** 콜아웃 3행 원문: "3,200 km 남음" / "점검까지 6개월" / "최초 교체까지 7.6년" / "+340 km 초과" */
fun cycleLine3(ctx: Context, s: ConsumableState, unit: DistanceUnit): String {
    val r = s.result
    if (r.inputError) return ctx.getString(R.string.remaining_input_error)
    if (r.status == Status.UNKNOWN) return ctx.getString(R.string.remaining_record_needed, verb(ctx, s.kind))
    val km = r.remainingKm
    val days = r.remainingDays
    if (r.status == Status.OVERDUE) {
        val over = if (r.limitingFactor == Factor.DISTANCE && s.cycle.overflowKm > 0) distance(ctx, s.cycle.overflowKm.toDouble(), unit)
        else duration(ctx, days ?: 0)
        return ctx.getString(R.string.diag_over, over)
    }
    val distText = km?.let { distance(ctx, it.toDouble(), unit) }
    val timeText = days?.let { duration(ctx, it) }
    val value = when {
        distText != null && timeText != null && r.status != Status.GOOD -> "$distText · $timeText"
        r.limitingFactor == Factor.TIME && timeText != null -> timeText
        distText != null -> distText
        timeText != null -> timeText
        else -> return ""
    }
    return when {
        s.kind == CycleKind.INSPECT -> ctx.getString(R.string.diag_until_inspect, value)
        s.usesFirstInterval -> ctx.getString(R.string.diag_until_first, value)
        else -> ctx.getString(R.string.diag_remaining, value)
    }
}

/** 링 라벨: INSPECT "경과", 그 외 "사용" (DG2-I1) */
fun usageLabel(ctx: Context, kind: CycleKind): String =
    ctx.getString(if (kind == CycleKind.INSPECT) R.string.diag_elapsed_word else R.string.diag_usage_word)

/** 라이프 루프 아래 제한 요인 문구: "거리 기준 · 3,200 km 남음" */
fun factorLine(ctx: Context, s: ConsumableState, unit: DistanceUnit): String? {
    val basis = when (s.result.limitingFactor) {
        Factor.DISTANCE -> ctx.getString(R.string.diag_basis_distance)
        Factor.TIME -> ctx.getString(R.string.diag_basis_time)
        null -> return null
    }
    return ctx.getString(R.string.diag_factor, basis, cycleLine3(ctx, s, unit))
}

fun cycleText(ctx: Context, index: Int, estimated: Boolean): String =
    ctx.getString(R.string.diag_cycle, index) + if (estimated) ctx.getString(R.string.diag_estimated_suffix) else ""

fun diagramCyclesOf(ctx: Context, states: List<ConsumableState>, unit: DistanceUnit): List<CycleUi> = states.map { s ->
    val c = s.cycle
    val status = s.displayStatus
    val word = statusLabel(ctx, status, s.kind)
    val label = usageLabel(ctx, s.kind)
    val pct = c.cycleProgress?.let { (it * 100).toInt() }
    val usage = pct?.let { ctx.getString(R.string.diag_usage_fmt, label, it) }
    val cycle = cycleText(ctx, c.cycleIndex, c.estimated)
    val line2 = if (usage != null) "$usage · $cycle" else word
    val raw3 = cycleLine3(ctx, s, unit)
    val line3 = if (s.item.isUserDefault && s.kind == CycleKind.USER) ctx.getString(R.string.diag_user_prefix, raw3) else raw3
    val name = itemName(ctx, s.item)
    val desc = if (pct != null) ctx.getString(R.string.diag_desc, name, label, pct, cycle, raw3, word)
    else ctx.getString(R.string.diag_desc_unknown, name, word, raw3)
    CycleUi(
        id = s.item.id, name = name, status = status,
        cycleProgress = c.cycleProgress, cycleIndex = c.cycleIndex, estimated = c.estimated,
        kind = s.kind, userDefault = s.item.isUserDefault && s.kind == CycleKind.USER, usageLabel = label,
        usageText = usage, line2 = line2, line3 = line3, factorText = factorLine(ctx, s, unit), statusWord = word,
        sensorAlert = s.sensorAlert != null, description = desc,
    )
}

/** 사이클 이력 요약 한 줄 (DG-42): "평균 교체 간격 15,400 km · 11.6개월 (권장 15,000 km / 12개월)" */
fun cycleSummaryLine(ctx: Context, avgKm: Double?, avgMonths: Double?, intervalText: String, unit: DistanceUnit, kind: CycleKind): String {
    val parts = listOfNotNull(
        avgKm?.let { distance(ctx, it, unit, round = false) },
        avgMonths?.let { ctx.getString(R.string.fmt_months_decimal, (it * 10).roundToInt() / 10.0) },
    )
    return ctx.getString(R.string.cycle_summary, verb(ctx, kind), parts.joinToString(" · "), intervalText)
}
