package com.example.consumablelife.feature.consumables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.consumablelife.R
import com.example.consumablelife.core.designsystem.AppCard
import com.example.consumablelife.core.designsystem.Dot
import com.example.consumablelife.core.designsystem.KeyValueRow
import com.example.consumablelife.core.designsystem.LifeBar
import com.example.consumablelife.core.designsystem.LocalAppColors
import com.example.consumablelife.core.designsystem.LocalMotion
import com.example.consumablelife.core.designsystem.LocalUi
import com.example.consumablelife.core.designsystem.Ring
import com.example.consumablelife.core.designsystem.Seg
import com.example.consumablelife.core.designsystem.Segmented
import com.example.consumablelife.core.designsystem.StatusPill
import com.example.consumablelife.core.designsystem.diagram.HotspotDiagram
import com.example.consumablelife.core.domain.DiagramView
import com.example.consumablelife.core.domain.Status
import com.example.consumablelife.ui.ItemUi

/* 설계서 v3 8장 — 탭2 대시보드: 좌측 열(도식 카드 + 가장 급한 항목) · 카드 그리드 2×5 · 상세 패널 */

@Composable
internal fun DashboardTab(s: TabScope) {
    val ui = LocalUi.current
    val current = s.current ?: return
    val urgent = s.urgent ?: return

    if (s.compact) {
        // 13장 Medium 이하: 좌측 열 숨김, '가장 급한 항목'은 첫 카드로, 상세는 시트
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cols = if (maxWidth >= 700.dp) 2 else 1
            LazyVerticalGrid(
                GridCells.Fixed(cols), Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(ui.dp(10)), verticalArrangement = Arrangement.spacedBy(ui.dp(10)),
            ) {
                items(s.items, key = { it.id }) { it ->
                    ItemCard(it, selected = it.id == current.id, Modifier.height(ui.dp(112))) { s.onSelect(it.id); s.onDetail(it.id) }
                }
            }
        }
        return
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(ui.gap)) {
        // ── 좌측 열 ──
        Column(Modifier.width(ui.dp(290)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(ui.gap)) {
            CarCard(s, current, Modifier.weight(1f).fillMaxWidth())
            UrgentCard(urgent, Modifier.fillMaxWidth().height(ui.dp(118))) { s.onSelect(urgent.id) }
        }
        // ── 카드 그리드: 2열 × 5행이 본문 높이에 꼭 맞고, 10개를 넘으면 그리드만 세로 스크롤 (DC-03) ──
        BoxWithConstraints(Modifier.width(ui.dp(410)).fillMaxHeight()) {
            val gap = ui.dp(10)
            val cell = (maxHeight - gap * 4) / 5
            LazyVerticalGrid(
                GridCells.Fixed(2), Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(s.items, key = { it.id }) { it ->
                    // DC-02 탭 → selectedId 갱신 → 도식 마커·상세 패널 즉시 반영 (화면 전환 없음)
                    ItemCard(it, selected = it.id == current.id, Modifier.height(cell)) { s.onSelect(it.id) }
                }
            }
        }
        // ── 상세 패널 ──
        DetailPanel(current, s, Modifier.weight(1f).fillMaxHeight())
    }
}

/** 도식 카드: 소형 도식 + 선택 항목 위치 1행 + [실물 | 투시 | 확대] 세그먼트 */
@Composable
private fun CarCard(s: TabScope, current: ItemUi, modifier: Modifier) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    var zoom by rememberSaveable { mutableStateOf(false) }
    val spots = remember(s.items) { s.items.toHotspots() }
    val view = s.data.settings.diagramView
    AppCard(modifier) {
        Column(
            Modifier.fillMaxSize().padding(ui.dp(16)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ui.dp(10), Alignment.CenterVertically),
        ) {
            HotspotDiagram(
                spec = s.spec, spots = spots, view = view, selectedId = current.id,
                onSelect = { id -> if (id != null) s.onSelect(id) },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                zoom = zoom, animationKey = "car:dashboard", lowPressure = s.data.tires,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(8))) {
                Dot(app.statusColor(current.status))
                Text(current.shortName, style = ui.labelBold, color = app.text, maxLines = 1)
                current.locationText?.let { Text(it, style = ui.caption, color = app.text2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Segmented(
                listOf(Seg(stringResource(R.string.diag_view_exterior)), Seg(stringResource(R.string.diag_view_xray)), Seg(stringResource(R.string.diag_zoom_toggle))),
                selected = when { zoom -> 2; view == DiagramView.XRAY -> 1; else -> 0 },
                onSelect = { i ->
                    when (i) {
                        2 -> zoom = !zoom
                        else -> { zoom = false; s.onView(if (i == 1) DiagramView.XRAY else DiagramView.EXTERIOR) }
                    }
                },
            )
        }
    }
}

/** 가장 급한 항목 카드 (due 테두리): 링 + 이름 + '후 교체' + 칩 */
@Composable
private fun UrgentCard(item: ItemUi, modifier: Modifier, onClick: () -> Unit) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val color = app.statusColor(item.status)
    val alarming = item.status != Status.GOOD && item.status != Status.UNKNOWN
    AppCard(modifier.clickable(role = Role.Button, onClick = onClick), borderColor = if (alarming) color else app.line) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = ui.dp(16), vertical = ui.dp(12)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(14)),
        ) {
            Ring(item.percent?.let { it / 100f }, color, ui.dp(74), item.percentText)
            Column(verticalArrangement = Arrangement.spacedBy(ui.dp(3))) {
                Text(stringResource(R.string.dash_hero_label), style = ui.caption, color = app.text3, maxLines = 1)
                Text(item.name, style = ui.body.copy(fontWeight = FontWeight.Bold), color = app.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.remainText} ${item.remainSuffix}".trim(), style = ui.label, color = app.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(item.status, item.statusLabel, small = true)
            }
        }
    }
}

/** 8.1 ItemCard: 링(좌) + shortName + 주기 종류 캡션 + 칩 + 남은 값. 선택 = accent 2dp 테두리 */
@Composable
private fun ItemCard(item: ItemUi, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val color = app.statusColor(item.status)
    AppCard(
        modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = item.description; this.selected = selected },
        borderColor = if (selected) app.accent else app.line, borderWidth = if (selected) 2.dp else 1.dp,
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = ui.dp(12), end = ui.dp(10), top = ui.dp(8), bottom = ui.dp(8)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(11)),
        ) {
            Ring(item.percent?.let { it / 100f }, color, ui.dp(50), item.percentText)
            Column(Modifier.weight(1f)) {
                Text(item.shortName, style = ui.labelBold, color = app.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.kindText, style = ui.caption, color = app.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(ui.dp(4)))
                StatusPill(item.status, item.statusLabel, small = true)
                Spacer(Modifier.height(ui.dp(4)))
                Text(item.remainText, style = ui.caption, color = app.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 8.2 상세 패널(인라인): 이름 + 칩 + 메타 → 남은 거리/기간 + 바 → 키값 4행 → 차량 데이터 → 사용 사이클 박스 → 이력 1건 → 하단 버튼.
 * 메타('기본값(수정 가능)' 등)는 목록이 아니라 여기서만 노출한다.
 */
@Composable
private fun DetailPanel(item: ItemUi, s: TabScope, modifier: Modifier) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val motion = LocalMotion.current
    AppCard(modifier) {
        AnimatedContent(
            targetState = item, contentKey = { it.id },
            transitionSpec = {
                if (motion.full) fadeIn(tween(240)) togetherWith fadeOut(tween(160))
                else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            },
            label = "detailPanel",
        ) { it ->
            val color = app.statusColor(it.status)
            Column(Modifier.fillMaxSize().padding(ui.dp(18))) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ui.dp(5))) {
                            Text(it.name, style = ui.title, color = app.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            StatusPill(it.status, it.statusLabel)
                        }
                        MoreLink { s.onDetail(it.id) }
                    }
                    Text(
                        it.meta(stringResource(R.string.user_default_meta)), style = ui.caption, color = app.text3,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = ui.dp(6)),
                    )
                    it.sensorText?.let { t -> Text(t, style = ui.caption, color = app.status.overdue, maxLines = 2, modifier = Modifier.padding(top = ui.dp(6))) }
                    Row(Modifier.fillMaxWidth().padding(top = ui.dp(14), bottom = ui.dp(8)), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.dist_remaining), style = ui.caption, color = app.text3, modifier = Modifier.weight(1f))
                        Text(it.distLine, style = ui.labelBold, color = app.text, maxLines = 1)
                    }
                    LifeBar(it.remainFraction, color, height = ui.dp(8))
                    Spacer(Modifier.height(ui.dp(4)))
                    KeyValueRow(it.dueLabel, it.dueText)
                    KeyValueRow(it.lastLabel, it.lastText)
                    KeyValueRow(stringResource(R.string.detail_interval), it.cycleText)
                    it.basisText?.let { b -> KeyValueRow(stringResource(R.string.detail_source), if (it.needsVerify) "$b ⓘ" else b) }
                    // 차량 데이터: 품목과 매칭된 Pleos Vehicle API 값
                    it.live.forEach { f ->
                        KeyValueRow(f.label, f.value, valueColor = if (f.warn) app.status.overdue else if (f.linked) null else app.text3)
                    }
                    // 사용 사이클 박스
                    Column(
                        Modifier.padding(top = ui.dp(12)).fillMaxWidth().background(app.surface2, RoundedCornerShape(ui.dp(10))).padding(horizontal = ui.dp(12), vertical = ui.dp(10)),
                        verticalArrangement = Arrangement.spacedBy(ui.dp(7)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.cycle_box, it.cycleIndex) + if (it.estimated) " " + stringResource(R.string.diag_estimated_suffix) else "",
                                style = ui.caption, color = app.text2, maxLines = 1, modifier = Modifier.weight(1f),
                            )
                            Text(it.cycleUsageText, style = ui.caption.copy(fontWeight = FontWeight.SemiBold), color = app.text, maxLines = 1)
                        }
                        LifeBar(it.ringFill, color)
                    }
                    // 이력: 최근 1건 (수정·삭제는 상세 시트에서)
                    Row(Modifier.fillMaxWidth().padding(top = ui.dp(12), bottom = ui.dp(6))) {
                        Text(stringResource(R.string.hist_n, it.historyCount), style = ui.caption, color = app.text3, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.detail_record_hint), style = ui.caption, color = app.text3)
                    }
                    it.lastRecord?.let { (date, info) ->
                        Row(
                            Modifier.fillMaxWidth().background(app.surface2, RoundedCornerShape(ui.dp(9))).clickable { s.onDetail(it.id) }.padding(horizontal = ui.dp(10), vertical = ui.dp(8)),
                        ) {
                            Text(date, style = ui.caption, color = app.text2, modifier = Modifier.weight(1f))
                            Text(info, style = ui.caption, color = app.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (!it.editable) {
                        Text(stringResource(R.string.interval_locked), style = ui.caption, color = app.text3, modifier = Modifier.padding(top = ui.dp(8)))
                    }
                }
                Spacer(Modifier.height(ui.dp(10)))
                ItemActions(it, s, fullVerb = true)
            }
        }
    }
}
