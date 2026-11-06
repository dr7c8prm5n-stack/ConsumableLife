package com.example.consumablelife.feature.consumables

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.consumablelife.R
import com.example.consumablelife.core.designsystem.AppCard
import com.example.consumablelife.core.designsystem.Dot
import com.example.consumablelife.core.designsystem.EaseOutCubic
import com.example.consumablelife.core.designsystem.LocalAppColors
import com.example.consumablelife.core.designsystem.LocalUi
import com.example.consumablelife.core.designsystem.Ring
import com.example.consumablelife.core.designsystem.StatusPill
import com.example.consumablelife.core.designsystem.diagram.DiagramSpec
import com.example.consumablelife.core.designsystem.diagram.HotspotDiagram
import com.example.consumablelife.core.designsystem.diagram.HotspotUi
import com.example.consumablelife.core.designsystem.formatNumber
import com.example.consumablelife.core.designsystem.motionSpec
import com.example.consumablelife.core.domain.DiagramView
import com.example.consumablelife.core.domain.DistanceUnit
import com.example.consumablelife.core.domain.Factor
import com.example.consumablelife.core.domain.Status
import com.example.consumablelife.core.domain.WheelPos
import com.example.consumablelife.core.domain.kmToMiles
import com.example.consumablelife.ui.Bucket
import com.example.consumablelife.ui.ItemUi
import kotlin.math.roundToInt

/* 설계서 v3 9장 — 탭3 타임라인 "무엇을 언제": 히어로(가장 급한 항목) + 거리/기간 레인 + 여유 칩 */

/** TL-01 축 범위: 거리 0 → 15,000 km, 기간 0 → 12개월 */
internal const val LANE_KM_MAX = 15_000
internal const val LANE_MONTH_MAX = 12

@Composable
internal fun TimelineTab(s: TabScope) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val current = s.current ?: return
    val unit = s.data.settings.distanceUnit

    // TL-02: 항목은 제한 요인(DISTANCE/TIME)에 따라 한 레인에만. 초과(OVERDUE)는 축의 '지금' 위치
    val scheduled = s.items.filter { it.status != Status.UNKNOWN }
    val byKm = scheduled.filter { it.limiting == Factor.DISTANCE && it.remainKm != null }.sortedBy { it.remainKm }
    val byTime = scheduled.filter { it.limiting == Factor.TIME && it.remainMonths != null }.sortedBy { it.remainMonths }
    val kmMarks = byKm.filter { it.remainKm!! <= LANE_KM_MAX }.map { LaneMark(it, (it.remainKm!!.coerceAtLeast(0)).toFloat() / LANE_KM_MAX, kmText(it.remainKm!!.coerceAtLeast(0), unit)) }
    val moMarks = byTime.filter { it.remainMonths!! <= LANE_MONTH_MAX }.map { LaneMark(it, (it.remainMonths!!.coerceAtLeast(0.0) / LANE_MONTH_MAX).toFloat(), it.remainValue) }
    val kmBeyond = byKm.filter { it.remainKm!! > LANE_KM_MAX }
    val moBeyond = byTime.filter { it.remainMonths!! > LANE_MONTH_MAX }
    val kmTicks = listOf(0, 5_000, 10_000, 15_000).map { it.toFloat() / LANE_KM_MAX to kmText(it, unit) }
    val moTicks = listOf(0, 3, 6, 9, 12).map { it.toFloat() / LANE_MONTH_MAX to stringResource(R.string.fmt_months, it) }

    val hero: @Composable (Modifier) -> Unit = { m ->
        HeroCard(
            item = current, spec = s.spec, tires = s.data.tires, view = DiagramView.XRAY, still = false,
            subLine = stringResource(R.string.tl_hero_meta, current.meta(stringResource(R.string.user_default_meta)), current.cycleText),
            modifier = m, ringSize = ui.dp(104), miniWidth = if (s.compact) 0.dp else ui.dp(200),
            // TL-07 다른 항목을 선택했으면 '가장 급한 항목으로' 버튼
            onBackToUrgent = if (s.urgent != null && current.id != s.urgent.id) ({ s.onSelect(null) }) else null,
            onClick = { s.onDetail(current.id) },
            actions = { ItemActions(current, s, vertical = true, fullVerb = true) },
        )
    }
    val lanes: @Composable (Modifier) -> Unit = { m ->
        AppCard(m) {
            Column(Modifier.fillMaxSize().padding(start = ui.dp(22), end = ui.dp(22), top = ui.dp(16), bottom = ui.dp(12))) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(14))) {
                    Text(stringResource(R.string.tl_title), style = ui.body.copy(fontWeight = FontWeight.Bold), color = app.text, maxLines = 1)
                    Text(
                        stringResource(R.string.tl_hint), style = ui.caption, color = app.text3, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (!s.compact) Legend()
                }
                Lane(
                    stringResource(R.string.tl_lane_km), stringResource(R.string.tl_lane_km_sub, kmText(LANE_KM_MAX, unit)),
                    kmMarks, kmTicks, stringResource(R.string.tl_beyond, kmText(LANE_KM_MAX, unit)), kmBeyond, current.id, s,
                    Modifier.weight(1f).fillMaxWidth(),
                )
                Lane(
                    stringResource(R.string.tl_lane_time), stringResource(R.string.tl_lane_time_sub, LANE_MONTH_MAX),
                    moMarks, moTicks, stringResource(R.string.tl_beyond, stringResource(R.string.fmt_months, LANE_MONTH_MAX)), moBeyond, current.id, s,
                    Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
    val spare: @Composable (Modifier) -> Unit = { m ->
        // 여유 칩: GOOD 항목 (기록 없는 항목도 여기서 접근)
        val rest = s.items.filter { it.status == Status.GOOD || it.status == Status.UNKNOWN }
        Row(m.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(7))) {
            Text(stringResource(R.string.tl_spare), style = ui.label, color = app.text2, maxLines = 1)
            Text("${s.counts[Bucket.GOOD] ?: 0}", style = ui.labelBold.copy(fontWeight = FontWeight.Bold), color = app.text, maxLines = 1, modifier = Modifier.padding(end = ui.dp(4)))
            rest.forEach { it -> SpareChip(it, it.id == current.id, { s.onSelect(it.id) }, { s.onDetail(it.id) }) }
        }
    }

    if (s.compact) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ui.gap)) {
            hero(Modifier.fillMaxWidth().heightIn(min = ui.dp(180)))
            lanes(Modifier.fillMaxWidth().height(ui.dp(440)))
            spare(Modifier.fillMaxWidth().height(ui.dp(40)))
        }
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ui.gap)) {
        hero(Modifier.fillMaxWidth().height(ui.dp(140)))
        lanes(Modifier.fillMaxWidth().weight(1f))
        spare(Modifier.fillMaxWidth().height(ui.dp(40)))
    }
}

private fun kmText(km: Int, unit: DistanceUnit): String =
    if (unit == DistanceUnit.MILE) formatNumber(kmToMiles(km.toDouble()).roundToInt()) + " mi" else formatNumber(km) + " km"

/**
 * 히어로 카드 (타임라인 상단 · S5 주행 중 공용): 링(사용/경과) + [칩 / 이름 / 1행 / 2행] + 미니 도식(해당 부위만) + 세로 버튼.
 * @param still S5: 정적(실물 고정, 모션 없음, 버튼 없음)
 */
@Composable
internal fun HeroCard(
    item: ItemUi,
    spec: DiagramSpec,
    tires: Map<WheelPos, Boolean>,
    view: DiagramView,
    still: Boolean,
    subLine: String,
    modifier: Modifier,
    ringSize: Dp,
    miniWidth: Dp,
    onBackToUrgent: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val color = app.statusColor(item.status)
    val alarming = item.status != Status.GOOD && item.status != Status.UNKNOWN
    val spots = remember(item.id, item.status, item.percentText) { listOf(HotspotUi(item.id, item.status, item.percentText, item.description)) }
    AppCard(
        modifier.then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        borderColor = if (alarming) color else app.line,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = ui.dp(22), vertical = ui.dp(14)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(22)),
        ) {
            Ring(item.percent?.let { it / 100f }, color, ringSize, item.percentText, sub = item.metricLabel)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ui.dp(3))) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(10))) {
                    StatusPill(item.status, item.statusLabel)
                    if (onBackToUrgent != null) {
                        Row(
                            Modifier.clip(RoundedCornerShape(ui.dp(8))).clickable(role = Role.Button, onClick = onBackToUrgent).padding(horizontal = ui.dp(6), vertical = ui.dp(4)),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(4)),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = app.accent, modifier = Modifier.size(ui.dp(14)))
                            Text(stringResource(R.string.tl_back_urgent), style = ui.caption.copy(fontWeight = FontWeight.SemiBold), color = app.accent, maxLines = 1)
                        }
                    }
                }
                Text(item.name, style = ui.headline, color = app.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.tl_hero_line, item.remainText, item.remainSuffix, item.dueText).replace("  ", " "),
                    style = ui.bodyL, color = app.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(item.sensorText ?: subLine, style = ui.caption, color = if (item.sensorText != null) app.status.overdue else app.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (miniWidth > 0.dp) {
                HotspotDiagram(
                    spec = spec, spots = spots, view = view, selectedId = item.id, onSelect = null,
                    modifier = Modifier.width(miniWidth), still = still,
                    animationKey = "car:mini", lowPressure = tires,
                )
            }
            if (actions != null) Box(Modifier.width(ui.dp(140))) { actions() }
        }
    }
}

private class LaneMark(val item: ItemUi, val fraction: Float, val value: String)

/** 9.1 레인: 이름 열 + 축(눈금·'지금'·마커·라벨) + '이후' 박스 */
@Composable
private fun Lane(
    title: String, sub: String, marks: List<LaneMark>, ticks: List<Pair<Float, String>>,
    beyondTitle: String, beyond: List<ItemUi>, selectedId: String, s: TabScope, modifier: Modifier,
) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(ui.dp(112))) {
            Text(title, style = ui.labelBold, color = app.text, maxLines = 1)
            Text(sub, style = ui.caption, color = app.text2, maxLines = 1)
        }
        LaneAxis(marks, ticks, selectedId, s, Modifier.weight(1f).fillMaxHeight().padding(end = ui.dp(10)))
        // 범위를 넘는 항목은 '이후' 박스에 이름·남은 값
        Column(
            Modifier.padding(start = ui.dp(14)).width(ui.dp(170)).heightIn(min = ui.dp(88)).background(app.surface2, RoundedCornerShape(ui.dp(12))).padding(horizontal = ui.dp(12), vertical = ui.dp(10)),
            verticalArrangement = Arrangement.spacedBy(ui.dp(4), Alignment.CenterVertically),
        ) {
            Text(beyondTitle, style = ui.caption.copy(fontWeight = FontWeight.SemiBold), color = app.text3, maxLines = 1)
            if (beyond.isEmpty()) Text(stringResource(R.string.tl_none), style = ui.caption, color = app.text2)
            beyond.forEach { it ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(ui.dp(6))).clickable(role = Role.Button) { s.onSelect(it.id) }) {
                    Text(it.shortName, style = ui.caption, color = if (it.id == selectedId) app.accent else app.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(it.remainValue, style = ui.caption, color = app.text2, maxLines = 1)
                }
            }
        }
    }
}

/**
 * 레인 축. 라벨은 실제 측정한 폭으로 배치한다:
 *  TL-03 index 홀짝으로 위/아래 교차, x > 88%면 우측 정렬
 *  TL-04 같은 쪽 라벨과 가로로 겹치면 반대편으로 → 그래도 겹치면 이름만(값 숨김)으로 축약
 *  TL-05 축 좌단 '지금' 마커 고정, 마커 x는 300ms로 이동 (AN-06)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LaneAxis(marks: List<LaneMark>, ticks: List<Pair<Float, String>>, selectedId: String, s: TabScope, modifier: Modifier) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val dot = ui.dp(16)
    val gap = ui.dp(8)
    val fractions = marks.map { m ->
        key(m.item.id) { animateFloatAsState(minOf(0.97f, m.fraction), motionSpec(tween(300, easing = EaseOutCubic)), label = "tl${m.item.id}") }
    }
    Layout(
        modifier = modifier.drawBehind {
            val y = size.height / 2
            drawLine(app.line2, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            ticks.forEach { (f, _) -> drawLine(app.line2, Offset(size.width * f, y), Offset(size.width * f, y + 6.dp.toPx()), strokeWidth = 1.dp.toPx()) }
        },
        content = {
            ticks.forEachIndexed { i, (_, text) ->
                Text(text, style = ui.caption, color = app.text3, maxLines = 1, modifier = Modifier.layoutId("tick$i"))
            }
            // '지금' 마커
            Box(Modifier.layoutId("now").size(ui.dp(14)).background(app.text, CircleShape).border(ui.dp(3), app.surface, CircleShape))
            marks.forEachIndexed { i, m ->
                val on = m.item.id == selectedId
                val color = app.statusColor(m.item.status)
                val click = Modifier
                    .combinedClickable(role = Role.Button, onClick = { s.onSelect(m.item.id) }, onLongClick = { s.onDetail(m.item.id) }) // TL-06
                    .semantics { contentDescription = m.item.description; selected = on }
                Box(Modifier.layoutId("dot$i").size(dot).background(color, CircleShape).border(ui.dp(2.5f), app.surface, CircleShape))
                LaneLabel(m.item.shortName, m.value, color, on, m.item.status, Modifier.layoutId("full$i").then(click))
                LaneLabel(m.item.shortName, null, color, on, m.item.status, Modifier.layoutId("short$i").then(click))
            }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val loose = Constraints()
        val by = measurables.associateBy { it.layoutId as String }
        val tickP = ticks.indices.map { by.getValue("tick$it").measure(loose) }
        val nowP = by.getValue("now").measure(loose)
        val dots = marks.indices.map { by.getValue("dot$it").measure(loose) }
        val full = marks.indices.map { by.getValue("full$it").measure(loose) }
        val short = marks.indices.map { by.getValue("short$it").measure(loose) }
        val gapPx = gap.roundToPx()
        val cy = h / 2

        // 배치 계산: 위(0)·아래(1) 각각의 마지막 라벨 오른쪽 끝
        val lastRight = intArrayOf(Int.MIN_VALUE / 2, Int.MIN_VALUE / 2)
        data class Place(val x: Int, val side: Int, val compact: Boolean, val left: Int)
        val places = marks.indices.map { i ->
            val x = (w * fractions[i].value).roundToInt()
            fun leftOf(pw: Int) = (if (fractions[i].value > 0.88f) x + dots[i].width / 2 - pw else x - pw / 2).coerceIn(0, maxOf(0, w - pw))
            val prefer = i % 2
            var side = prefer
            var compact = false
            var left = leftOf(full[i].width)
            if (left < lastRight[side] + gapPx) {
                side = 1 - prefer
                if (left < lastRight[side] + gapPx) {
                    compact = true
                    left = leftOf(short[i].width)
                    side = if (left >= lastRight[prefer] + gapPx) prefer else if (lastRight[0] <= lastRight[1]) 0 else 1
                }
            }
            lastRight[side] = left + (if (compact) short[i].width else full[i].width)
            Place(x, side, compact, left)
        }
        layout(w, h) {
            ticks.forEachIndexed { i, (f, _) ->
                val p = tickP[i]
                val x = (w * f).roundToInt()
                val left = when (i) { 0 -> x; ticks.lastIndex -> x - p.width; else -> x - p.width / 2 }
                p.place(left, cy + 8.dp.roundToPx())
            }
            nowP.place(-nowP.width / 2, cy - nowP.height / 2)
            places.forEachIndexed { i, pl ->
                val label = if (pl.compact) short[i] else full[i]
                val y = if (pl.side == 0) cy - dots[i].height / 2 - gapPx - label.height else cy + dots[i].height / 2 + gapPx + tickP.first().height
                label.place(pl.left, y)
                dots[i].place(pl.x - dots[i].width / 2, cy - dots[i].height / 2)
            }
        }
    }
}

/** TL-03 라벨: 이름 bold / 값, surface2 면 + 상태색 테두리(양호는 line2). 선택 = accent 테두리 */
@Composable
private fun LaneLabel(name: String, value: String?, color: androidx.compose.ui.graphics.Color, selected: Boolean, status: Status, modifier: Modifier) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val shape = RoundedCornerShape(ui.dp(9))
    val border = when {
        selected -> app.accent
        status == Status.GOOD -> app.line2
        else -> color
    }
    Column(
        modifier.clip(shape).background(app.surface2).border(if (selected) ui.dp(1.6f) else ui.dp(1f), border, shape).padding(horizontal = ui.dp(9), vertical = ui.dp(4)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = ui.caption.copy(fontWeight = FontWeight.SemiBold), color = app.text, maxLines = 1, textAlign = TextAlign.Center)
        if (value != null) Text(value, style = ui.caption, color = app.text2, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpareChip(item: ItemUi, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val shape = RoundedCornerShape(ui.dp(9))
    Row(
        Modifier
            .height(ui.dp(32))
            .clip(shape)
            .background(app.surface)
            .border(if (selected) ui.dp(1.6f) else 1.dp, if (selected) app.accent else app.line, shape)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLong)
            .semantics { contentDescription = item.description; this.selected = selected }
            .padding(horizontal = ui.dp(10)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(7)),
    ) {
        Dot(app.statusColor(item.status))
        Text(item.shortName, style = ui.caption, color = app.text2, maxLines = 1)
        Text(
            item.remainValue,
            style = ui.caption.copy(fontWeight = FontWeight.SemiBold), color = app.text, maxLines = 1,
        )
    }
}
