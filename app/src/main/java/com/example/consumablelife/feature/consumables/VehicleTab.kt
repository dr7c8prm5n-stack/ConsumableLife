package com.example.consumablelife.feature.consumables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.consumablelife.R
import com.example.consumablelife.core.designsystem.ActionButton
import com.example.consumablelife.core.designsystem.AppCard
import com.example.consumablelife.core.designsystem.Dot
import com.example.consumablelife.core.designsystem.EaseOutQuint
import com.example.consumablelife.core.designsystem.IconTile
import com.example.consumablelife.core.designsystem.KeyValueRow
import com.example.consumablelife.core.designsystem.LifeBar
import com.example.consumablelife.core.designsystem.LocalAppColors
import com.example.consumablelife.core.designsystem.LocalMotion
import com.example.consumablelife.core.designsystem.LocalUi
import com.example.consumablelife.core.designsystem.PlayedAnimations
import com.example.consumablelife.core.designsystem.Ring
import com.example.consumablelife.core.designsystem.Seg
import com.example.consumablelife.core.designsystem.Segmented
import com.example.consumablelife.core.designsystem.StatusPill
import com.example.consumablelife.core.designsystem.diagram.HotspotDiagram
import com.example.consumablelife.core.designsystem.diagram.HotspotUi
import com.example.consumablelife.core.domain.DiagramView
import com.example.consumablelife.ui.Bucket
import com.example.consumablelife.ui.ItemUi
import com.example.consumablelife.ui.StatusFilter

/* 설계서 v3 7장 — 탭1 차량(메인): 도식 + 핫스팟 뱃지 + 플로팅 상세 카드 + 도크(필터·칩) */

internal fun List<ItemUi>.toHotspots(): List<HotspotUi> = map { HotspotUi(it.id, it.status, it.percentText, it.description) }

@Composable
internal fun VehicleTab(s: TabScope) {
    val ui = LocalUi.current
    var zoom by rememberSaveable { mutableStateOf(false) }
    var bright by rememberSaveable { mutableStateOf(false) }
    val spots = remember(s.items) { s.items.toHotspots() }
    val dimmed = remember(s.items, s.filter) {
        if (s.filter == StatusFilter.ALL) emptySet() else s.items.filter { it.bucket != s.filter.bucket }.map { it.id }.toSet()
    }
    val current = s.current ?: return

    val diagram: @Composable (Modifier) -> Unit = { m ->
        HotspotDiagram(
            spec = s.spec, spots = spots, view = s.data.settings.diagramView,
            // 선택이 없으면 가장 급한 항목의 마커를 강조 (DB-05)
            selectedId = current.id,
            onSelect = { id -> s.onSelect(id) },
            modifier = m,
            badges = !s.compact, dimmed = dimmed, zoom = zoom, bright = bright,
            intro = true, periodicScan = s.data.settings.periodicScan,
            animationKey = "car:vehicle", lowPressure = s.data.tires,
        )
    }

    if (s.compact) {
        // 13장 Medium 이하: 도식 → 상세 → 칩을 세로로 쌓고 스크롤
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ui.dp(12))) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Legend(Modifier.weight(1f))
                Modes(s, zoom, bright, { zoom = !zoom }, { bright = !bright })
            }
            diagram(Modifier.fillMaxWidth())
            DetailCard(current, s, Modifier.fillMaxWidth(), elevated = false)
            Dock(s, current, Modifier.fillMaxWidth())
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Legend(Modifier.align(Alignment.TopStart).padding(top = ui.dp(6), start = ui.dp(4)))
        Modes(s, zoom, bright, { zoom = !zoom }, { bright = !bright }, Modifier.align(Alignment.TopEnd))
        // 도식 캔버스: 도크 위 · 상세 카드 왼쪽 영역을 꽉 채운다 (비율 유지, 뱃지 여백 포함)
        diagram(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxSize()
                .padding(top = ui.dp(40), bottom = ui.dp(160 + 8), end = ui.dp(300 + 20)),
        )
        // AN-03 상세 카드: 처음 표시 시 우측에서 24dp 슬라이드
        val motion = LocalMotion.current
        val enter = remember { Animatable(if (PlayedAnimations.hasPlayed(CARD_KEY)) 1f else 0f) }
        LaunchedEffect(motion.full) {
            if (enter.value >= 1f) return@LaunchedEffect
            PlayedAnimations.markPlayed(CARD_KEY)
            if (motion.full) enter.animateTo(1f, tween(420, delayMillis = 500, easing = EaseOutQuint)) else enter.snapTo(1f)
        }
        DetailCard(
            current, s,
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = ui.dp(56))
                .width(ui.dp(300))
                .graphicsLayer { alpha = enter.value; translationX = (1f - enter.value) * 24.dp.toPx() },
            elevated = true,
        )
        Dock(s, current, Modifier.align(Alignment.BottomStart).fillMaxWidth().height(ui.dp(160)))
    }
}

private const val CARD_KEY = "vehicle:detailCard"

@Composable
internal fun Legend(modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ui.dp(12)), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            app.status.due to R.string.legend_due, app.status.caution to R.string.sum_caution, app.status.good to R.string.sum_good,
        ).forEach { (c, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6))) {
                Dot(c)
                Text(stringResource(label), style = ui.caption, color = app.text2, maxLines = 1)
            }
        }
    }
}

/** 도식 모드: 세그먼트 [실물 | 투시] + 아이콘 버튼 2개(확대, 밝기) */
@Composable
private fun Modes(s: TabScope, zoom: Boolean, bright: Boolean, onZoom: () -> Unit, onBright: () -> Unit, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ui.dp(8)), verticalAlignment = Alignment.CenterVertically) {
        ViewSegment(s.data.settings.diagramView, s.onView)
        IconTile(Icons.Filled.ZoomIn, stringResource(R.string.diag_zoom_toggle), onZoom, active = zoom)
        IconTile(Icons.Filled.LightMode, stringResource(R.string.diag_bright_toggle), onBright, active = bright)
    }
}

@Composable
internal fun ViewSegment(view: DiagramView, onView: (DiagramView) -> Unit, modifier: Modifier = Modifier) {
    Segmented(
        listOf(Seg(stringResource(R.string.diag_view_exterior)), Seg(stringResource(R.string.diag_view_xray))),
        selected = if (view == DiagramView.XRAY) 1 else 0,
        onSelect = { onView(if (it == 1) DiagramView.XRAY else DiagramView.EXTERIOR) },
        modifier = modifier,
    )
}

/**
 * 7.4 플로팅 상세 카드: 링 + 이름 + 칩 + 위치 → 큰 수치 → 남은 바 → 키값 3행(+차량 데이터) → 버튼 2개.
 * 항목이 바뀌면 내용만 크로스페이드 (AN-03, 240ms).
 */
@Composable
private fun DetailCard(item: ItemUi, s: TabScope, modifier: Modifier, elevated: Boolean) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val motion = LocalMotion.current
    AppCard(modifier, elevated = elevated) {
        AnimatedContent(
            targetState = item,
            contentKey = { it.id },
            transitionSpec = {
                if (motion.full) fadeIn(tween(240)) togetherWith fadeOut(tween(160))
                else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            },
            label = "detailCard",
        ) { it ->
            val color = app.statusColor(it.status)
            Column(Modifier.fillMaxWidth().padding(ui.dp(18))) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(14))) {
                    Ring(it.percent?.let { p -> p / 100f }, color, ui.dp(72), it.percentText)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ui.dp(4))) {
                        Text(it.name, style = ui.title, color = app.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StatusPill(it.status, it.statusLabel, small = true)
                        it.locationText?.let { loc -> Text(loc, style = ui.caption, color = app.text3, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    MoreLink { s.onDetail(it.id) }
                }
                Spacer(Modifier.height(ui.dp(12)))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(it.remainText, style = ui.number, color = app.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (it.remainSuffix.isNotEmpty()) {
                        Spacer(Modifier.width(ui.dp(6)))
                        Text(it.remainSuffix, style = ui.label, color = app.text3, maxLines = 1, modifier = Modifier.padding(bottom = ui.dp(3)))
                    }
                }
                Spacer(Modifier.height(ui.dp(6)))
                LifeBar(it.remainFraction, color, height = ui.dp(8))
                Spacer(Modifier.height(ui.dp(4)))
                it.sensorText?.let { t -> Text(t, style = ui.caption, color = app.status.overdue, maxLines = 2, modifier = Modifier.padding(top = ui.dp(4))) }
                KeyValueRow(it.dueLabel, it.dueText)
                KeyValueRow(stringResource(R.string.detail_interval), it.cycleText)
                KeyValueRow(it.lastLabel, it.lastText)
                // 품목과 매칭된 차량 API 값 (첫 줄만 — 전체는 '더 보기')
                it.live.firstOrNull()?.let { f ->
                    KeyValueRow(f.label, f.value, divider = false, valueColor = if (f.warn) app.status.overdue else if (f.linked) null else app.text3)
                }
                Spacer(Modifier.height(ui.dp(10)))
                ItemActions(it, s)
            }
        }
    }
}

@Composable
internal fun MoreLink(onClick: () -> Unit) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    Text(
        stringResource(R.string.more), style = ui.caption.copy(fontWeight = FontWeight.SemiBold), color = app.accent, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(ui.dp(8))).clickable(role = Role.Button, onClick = onClick).padding(horizontal = ui.dp(8), vertical = ui.dp(10)),
    )
}

/** '주기 수정 / 완료' — 선택 항목의 상세 안에서만 (DS-02: 정기표 항목의 주기 수정은 비활성) */
@Composable
internal fun ItemActions(item: ItemUi, s: TabScope, vertical: Boolean = false, fullVerb: Boolean = false) {
    val ui = LocalUi.current
    val edit: @Composable (Modifier) -> Unit = { m ->
        ActionButton(
            stringResource(R.string.detail_edit_interval), { s.onEditInterval(item.id) }, m,
            primary = false, small = true, icon = Icons.Filled.Edit, enabled = item.editable,
        )
    }
    val done: @Composable (Modifier) -> Unit = { m ->
        ActionButton(
            if (fullVerb) stringResource(R.string.action_done_verb, item.verb) else stringResource(R.string.action_done),
            { s.onRecord(item.id) }, m, small = true, icon = Icons.Filled.TaskAlt,
        )
    }
    if (vertical) {
        Column(verticalArrangement = Arrangement.spacedBy(ui.dp(8))) {
            edit(Modifier.fillMaxWidth().graphicsLayer { alpha = if (item.editable) 1f else 0.45f })
            done(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(ui.dp(8))) {
            edit(Modifier.weight(1f).graphicsLayer { alpha = if (item.editable) 1f else 0.45f })
            done(Modifier.weight(1f))
        }
    }
}

/** 7.3 도크: 필터 세그먼트(전체 n / 교체 n / 곧 점검 n / 양호 n) + 힌트 + 항목 칩(긴급도 순) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dock(s: TabScope, current: ItemUi, modifier: Modifier) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val list = if (s.filter == StatusFilter.ALL) s.items else s.items.filter { it.bucket == s.filter.bucket }
    AppCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = ui.dp(14), vertical = ui.dp(12)), verticalArrangement = Arrangement.spacedBy(ui.dp(9))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(10))) {
                Segmented(
                    listOf(
                        Seg(stringResource(R.string.filter_all, s.items.size)),
                        Seg(stringResource(R.string.filter_n, stringResource(R.string.sum_due), s.counts[Bucket.DUE] ?: 0), dot = app.status.due),
                        Seg(stringResource(R.string.filter_n, stringResource(R.string.sum_caution), s.counts[Bucket.CAUTION] ?: 0), dot = app.status.caution),
                        Seg(stringResource(R.string.filter_n, stringResource(R.string.sum_good), s.counts[Bucket.GOOD] ?: 0), dot = app.status.good),
                    ),
                    selected = s.filter.ordinal,
                    onSelect = { s.onFilter(StatusFilter.entries[it]) },
                )
                if (!s.compact) Text(stringResource(R.string.dock_hint), style = ui.caption, color = app.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FlowRow(
                Modifier.fillMaxWidth().then(if (s.compact) Modifier else Modifier.verticalScroll(rememberScrollState())),
                horizontalArrangement = Arrangement.spacedBy(ui.dp(8)), verticalArrangement = Arrangement.spacedBy(ui.dp(8)),
            ) {
                list.forEach { it ->
                    val on = it.id == current.id
                    val shape = RoundedCornerShape(ui.dp(10))
                    Row(
                        Modifier
                            .height(ui.dp(36))
                            .clip(shape)
                            .background(if (on) app.surface else app.surface2)
                            .then(if (on) Modifier.border(ui.dp(1.4f), app.accent, shape) else Modifier)
                            .clickable(role = Role.Button) { s.onSelect(it.id) }
                            .semantics { contentDescription = it.description; selected = on }
                            .padding(horizontal = ui.dp(12)),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(8)),
                    ) {
                        Dot(app.statusColor(it.status))
                        Text(it.shortName, style = ui.label, color = app.text, maxLines = 1)
                        Text(it.percentText, style = ui.label.copy(fontWeight = FontWeight.SemiBold), color = app.text3, maxLines = 1)
                    }
                }
            }
        }
    }
}
