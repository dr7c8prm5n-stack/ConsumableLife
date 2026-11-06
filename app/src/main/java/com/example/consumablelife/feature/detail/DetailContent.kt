package com.example.consumablelife.feature.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.consumablelife.R
import com.example.consumablelife.core.data.AppSettings
import com.example.consumablelife.core.data.ConsumableState
import com.example.consumablelife.core.data.DashboardData
import com.example.consumablelife.core.designsystem.BigButton
import com.example.consumablelife.core.designsystem.LinearLifeBar
import com.example.consumablelife.core.designsystem.LocalStatusColors
import com.example.consumablelife.core.designsystem.SectionCard
import com.example.consumablelife.core.designsystem.StatusChip
import com.example.consumablelife.core.designsystem.animatedPercent
import com.example.consumablelife.core.designsystem.animatedStatusColor
import com.example.consumablelife.core.designsystem.diagram.CycleBar
import com.example.consumablelife.core.designsystem.diagram.CycleHistoryChart
import com.example.consumablelife.core.designsystem.diagram.CycleUi
import com.example.consumablelife.core.designsystem.diagram.DiagramMode
import com.example.consumablelife.core.designsystem.diagram.Ioniq5Diagram
import com.example.consumablelife.core.designsystem.diagram.LifeLoop
import com.example.consumablelife.core.designsystem.diagram.LoopRefill
import com.example.consumablelife.core.designsystem.diagram.rememberDiagramSpec
import com.example.consumablelife.core.domain.CycleHistory
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.DiagramView
import com.example.consumablelife.core.domain.DistanceUnit
import com.example.consumablelife.core.domain.Factor
import com.example.consumablelife.core.domain.Interval
import com.example.consumablelife.core.domain.RecordAction
import com.example.consumablelife.core.domain.ReplacementRecord
import com.example.consumablelife.core.domain.Status
import com.example.consumablelife.core.domain.WheelPos
import com.example.consumablelife.ui.alertLabel
import com.example.consumablelife.ui.conditionLabel
import com.example.consumablelife.ui.cycleSummaryLine
import com.example.consumablelife.ui.cycleText
import com.example.consumablelife.ui.date
import com.example.consumablelife.ui.distance
import com.example.consumablelife.ui.duration
import com.example.consumablelife.ui.itemName
import com.example.consumablelife.ui.kindLabel
import com.example.consumablelife.ui.liveFactsOf
import com.example.consumablelife.ui.rememberCtx
import com.example.consumablelife.ui.sourceLabel
import com.example.consumablelife.ui.statusLabel
import com.example.consumablelife.ui.usageLabel
import com.example.consumablelife.ui.verb
import com.example.consumablelife.ui.zoneLabel
import java.time.LocalDate

/** DG2-AN-12 교체·점검 완료 리필 파라미터 (기록 저장 직후 S2 진입 시 1회) */
data class RefillRequest(val recordId: Long, val fromProgress: Float, val fromStatus: Status, val fromCycleIndex: Int)

/**
 * S2 항목 상세 본문 — 단독 화면(DetailScreen)과 대시보드 wide 마스터-디테일(UX-06)에서 공용.
 * 상단: 라이프 루프(명세 v2.0 7장) + 실물 위 X-ray 렌즈 FOCUS 도식(DG2-L5). 아래: 거리/기간 바, 사실 행, 사이클 이력, 기록.
 */
@Composable
fun DetailContent(
    state: ConsumableState,
    settings: AppSettings,
    cycles: List<CycleUi>,
    tires: Map<WheelPos, Boolean>,
    odoNowKm: Double,
    today: LocalDate,
    refill: RefillRequest?,
    onEditInterval: () -> Unit,
    onRecord: () -> Unit,
    onEditRecord: (ReplacementRecord) -> Unit,
    onDeleteRecord: (ReplacementRecord) -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
    /** 품목과 매칭된 차량 API 값을 보여 주려면 전달 */
    data: DashboardData? = null,
) {
    val ctx = rememberCtx()
    val r = state.result
    val unit = settings.distanceUnit
    val v = verb(ctx, state.kind)
    val ui = cycles.firstOrNull { it.id == state.item.id }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 센서 경고 카드 (조치 안내만, 진단 단정 금지)
        state.sensorAlert?.let { a ->
            val c = LocalStatusColors.current
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.overdue, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Filled.DirectionsCar, null, tint = c.onStatus, modifier = Modifier.size(36.dp))
                Text(
                    stringResource(R.string.alert_card, alertLabel(ctx, a)),
                    color = c.onStatus, style = MaterialTheme.typography.bodyMedium, maxLines = 3,
                )
            }
        }

        val gauge: @Composable (Modifier) -> Unit = { m ->
            SectionCard(m) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 명세 v2.0 7장 라이프 루프 (DG2-L1~L3, DG2-AN-12)
                    LifeLoop(
                        progress = state.cycle.cycleProgress, status = state.displayStatus,
                        cycleIndex = state.cycle.cycleIndex, estimated = state.cycle.estimated,
                        usageLabel = usageLabel(ctx, state.kind), cycleText = { cycleText(ctx, it, state.cycle.estimated) },
                        footer = ui?.factorText,
                        cautionAt = 1f - settings.cautionRatio, dueAt = 1f - settings.dueRatio,
                        refill = refill?.let { LoopRefill("refill:${it.recordId}", it.fromProgress, it.fromStatus, it.fromCycleIndex) },
                    )
                    StatusChip(state.displayStatus, statusLabel(ctx, state.displayStatus, state.kind), large = true)
                    Text(
                        itemName(ctx, state.item),
                        style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(kindLabel(ctx, state.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.item.isUserDefault && state.kind == CycleKind.USER) UserDefaultBadge()
                    }
                }
                val spec = rememberDiagramSpec()
                val hotspot = spec.hotspot(state.item.id)
                if (hotspot?.anchor != null) {
                    Text(
                        stringResource(R.string.car_location, zoneLabel(ctx, state.item.zone)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // DG2-L5 FOCUS: 실물 위 선택 앵커 중심 X-ray 렌즈 + 1.6배 확대 (부록 A DG-AN-05)
                    Ioniq5Diagram(
                        spec = spec, cycles = cycles, mode = DiagramMode.FOCUS, view = DiagramView.XRAY,
                        selectedId = state.item.id, onSelect = null,
                        animationKey = "car:detail:${state.item.id}",
                        lowPressure = tires, maxHeight = 360.dp,
                    )
                    if (hotspot.approx) {
                        Text(stringResource(R.string.diag_approx_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val facts: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionCard {
                    LimitBars(state, unit)
                    HorizontalDivider()
                    DueDateRow(state, v)
                    FactRow(
                        stringResource(R.string.detail_last, v),
                        state.lastRecord?.let {
                            "${date(it.date)} · ${distance(ctx, it.odometerKm, unit, round = false)}" +
                                if (state.estimated) " · ${stringResource(R.string.estimated)}" else ""
                        } ?: "—",
                    )
                    FactRow(stringResource(R.string.detail_kind), kindLabel(ctx, state.kind))
                    FactRow(
                        stringResource(R.string.detail_interval),
                        (if (state.usesFirstInterval) stringResource(R.string.detail_interval_first) else conditionLabel(ctx, settings.drivingCondition)) +
                            " · " + intervalText(state.interval, unit),
                    )
                    val spec = rememberDiagramSpec()
                    spec.hotspot(state.item.id)?.source?.let { src ->
                        FactRow(stringResource(R.string.detail_source), src)
                    }
                    if (state.item.needsConfirm) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.detail_needs_confirm), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                // 차량 데이터: 품목과 매칭된 Pleos Vehicle API 값 (연동 API가 없으면 그 사실을 표시)
                if (data != null) {
                    SectionCard {
                        Text(stringResource(R.string.live_title), style = MaterialTheme.typography.titleMedium)
                        liveFactsOf(ctx, state, data).forEach { f ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(f.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
                                Text(
                                    f.value, style = if (f.warn) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        f.warn -> LocalStatusColors.current.overdue
                                        f.linked -> MaterialTheme.colorScheme.onSurface
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.weight(0.6f), maxLines = 2,
                                )
                            }
                        }
                    }
                }
                // 명세 v2.0 DG2-L4 / 부록 A 10장: 사이클 이력 타임라인 (DG-40~42)
                val spans = remember(state.records, odoNowKm, today) { CycleHistory.spans(state.records, odoNowKm, today) }
                if (spans.isNotEmpty()) {
                    SectionCard {
                        Text(stringResource(R.string.cycle_history_title), style = MaterialTheme.typography.titleMedium)
                        val byKm = state.interval.km != null
                        val recommended = (if (byKm) state.interval.km else state.interval.months)?.toDouble() ?: 0.0
                        fun monthsText(m: Double) = ctx.getString(R.string.fmt_months_decimal, String.format(java.util.Locale.KOREA, "%.1f", m))
                        fun amount(x: Double) = if (byKm) distance(ctx, x, unit, round = false) else monthsText(x)
                        val bars = spans.map { sp ->
                            val value = if (byKm) (sp.km ?: 0.0) else sp.months
                            val over = value - recommended
                            CycleBar(
                                span = sp, value = value,
                                title = ctx.getString(R.string.cycle_n, sp.index) + if (sp.estimated) " " + ctx.getString(R.string.diag_estimated_suffix) else "",
                                range = sp.end?.let { ctx.getString(R.string.cycle_range, date(sp.start), date(it)) } ?: ctx.getString(R.string.cycle_range_open, date(sp.start)),
                                valueText = amount(value),
                                overText = if (over > 0 && recommended > 0) ctx.getString(R.string.cycle_over_suffix, amount(over)).trim() else null,
                            )
                        }
                        CycleHistoryChart(
                            bars = bars, recommended = recommended,
                            recommendedLabel = stringResource(R.string.cycle_recommended, intervalText(state.interval, unit)),
                            currentLabel = stringResource(R.string.cycle_current),
                        )
                        val sum = CycleHistory.summary(spans)
                        if (sum.avgKm != null || sum.avgMonths != null) {
                            Text(
                                cycleSummaryLine(ctx, sum.avgKm, sum.avgMonths, intervalText(state.interval, unit), unit, state.kind),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                History(state.records, unit, onEditRecord, onDeleteRecord)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BigButton(
                        stringResource(R.string.detail_edit_interval), onEditInterval,
                        Modifier.weight(1f), icon = Icons.Filled.Edit, primary = false,
                    )
                    BigButton(
                        stringResource(R.string.detail_replaced, v), onRecord,
                        Modifier.weight(1f), icon = Icons.Filled.TaskAlt,
                    )
                }
            }
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                gauge(Modifier.weight(0.45f))
                facts(Modifier.weight(0.55f))
            }
        } else {
            gauge(Modifier)
            facts(Modifier)
        }
    }
}

/** DG2-I2 USER 품목 "기본값 · 수정 가능" 배지 — 상세에서만 노출 */
@Composable
fun UserDefaultBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.user_default_badge), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun LimitBars(state: ConsumableState, unit: DistanceUnit) {
    val ctx = rememberCtx()
    val r = state.result
    val color by animatedStatusColor(state.displayStatus)
    val distRatio by animatedPercent(((r.distRatio ?: 0.0) * 100).toFloat().coerceIn(0f, 100f))
    val timeRatio by animatedPercent(((r.timeRatio ?: 0.0) * 100).toFloat().coerceIn(0f, 100f))
    val interval = state.interval

    interval.km?.let { total ->
        val km = r.remainingKm
        val emph = r.limitingFactor == Factor.DISTANCE
        BarRow(
            label = stringResource(R.string.detail_distance),
            value = when {
                km == null -> "—"
                km < 0 -> stringResource(R.string.detail_over_of, distance(ctx, -km.toDouble(), unit), distance(ctx, total.toDouble(), unit, false))
                else -> stringResource(R.string.detail_remaining_of, distance(ctx, km.toDouble(), unit), distance(ctx, total.toDouble(), unit, false))
            },
            emphasized = emph,
        ) { LinearLifeBar({ distRatio / 100f }, { color }, emph) }
    }
    interval.months?.let { total ->
        val days = r.remainingDays
        val emph = r.limitingFactor == Factor.TIME
        BarRow(
            label = stringResource(R.string.detail_time),
            value = when {
                days == null -> "—"
                days < 0 -> stringResource(R.string.detail_over_of, duration(ctx, days), stringResource(R.string.fmt_months, total))
                else -> stringResource(R.string.detail_remaining_of, duration(ctx, days), stringResource(R.string.fmt_months, total))
            },
            emphasized = emph,
        ) { LinearLifeBar({ timeRatio / 100f }, { color }, emph) }
    }
    if (state.result.status == Status.UNKNOWN) {
        Text(
            if (r.inputError) stringResource(R.string.remaining_input_error) else stringResource(R.string.remaining_record_needed, verb(ctx, state.kind)),
            style = MaterialTheme.typography.bodyMedium, color = LocalStatusColors.current.unknown,
        )
    }
}

@Composable
private fun BarRow(label: String, value: String, emphasized: Boolean, bar: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(64.dp),
            )
            Text(
                value, style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        bar()
    }
}

@Composable
private fun DueDateRow(state: ConsumableState, verbText: String) {
    val r = state.result
    val basis = when (r.limitingFactor) {
        Factor.DISTANCE -> if (r.collectingData && state.interval.months != null) stringResource(R.string.detail_basis_time)
        else stringResource(R.string.detail_basis_distance)
        Factor.TIME -> stringResource(R.string.detail_basis_time)
        null -> null
    }
    FactRow(
        stringResource(R.string.detail_due_date, verbText),
        r.dueDate?.let { "${date(it)} ($basis)" } ?: "—",
        emphasized = true,
    )
    if (r.collectingData && state.interval.km != null) {
        Text(
            stringResource(R.string.detail_collecting),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FactRow(label: String, value: String, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(
            value, style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f), maxLines = 2,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun History(
    records: List<ReplacementRecord>,
    unit: DistanceUnit,
    onEdit: (ReplacementRecord) -> Unit,
    onDelete: (ReplacementRecord) -> Unit,
) {
    val ctx = rememberCtx()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ReplacementRecord?>(null) }
    var confirmDelete by remember { mutableStateOf<ReplacementRecord?>(null) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.detail_history, records.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (records.isNotEmpty()) {
                Text(stringResource(R.string.detail_record_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (records.isEmpty()) {
            Text(stringResource(R.string.detail_history_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val shown = if (expanded) records else records.take(5)
        shown.forEach { rec ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .combinedClickable(onClick = {}, onLongClick = { menuFor = rec })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(date(rec.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.35f))
                Text(distance(ctx, rec.odometerKm, unit, round = false), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.35f))
                Text(
                    (if (rec.action == RecordAction.INSPECTED) stringResource(R.string.record_action_inspected) + " · " else "") + (rec.memo ?: sourceLabel(ctx, rec.source)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.3f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (records.size > 5) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 64.dp)) {
                Text(stringResource(if (expanded) R.string.detail_history_less else R.string.detail_history_more), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    menuFor?.let { rec ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text("${date(rec.date)} · ${distance(ctx, rec.odometerKm, unit, false)}") },
            confirmButton = {
                TextButton(onClick = { menuFor = null; onEdit(rec) }, Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.record_edit)) }
            },
            dismissButton = {
                TextButton(onClick = { menuFor = null; confirmDelete = rec }, Modifier.heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.record_delete), color = LocalStatusColors.current.overdue)
                }
            },
        )
    }
    confirmDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(R.string.record_delete_confirm), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; onDelete(rec) }, Modifier.heightIn(min = 64.dp)) {
                    Text(stringResource(R.string.record_delete), color = LocalStatusColors.current.overdue)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }, Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
fun intervalText(i: Interval, unit: DistanceUnit): String {
    val ctx = rememberCtx()
    val parts = listOfNotNull(
        i.km?.let { distance(ctx, it.toDouble(), unit, round = false) },
        i.months?.let { stringResource(R.string.fmt_months, it) },
    )
    return if (parts.isEmpty()) stringResource(R.string.interval_none) else parts.joinToString(" / ")
}
