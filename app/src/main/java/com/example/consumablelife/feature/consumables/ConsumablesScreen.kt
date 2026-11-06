package com.example.consumablelife.feature.consumables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.consumablelife.R
import com.example.consumablelife.app.LocalUx
import com.example.consumablelife.core.data.ConsumableRepository
import com.example.consumablelife.core.data.ConsumableState
import com.example.consumablelife.core.data.DashboardData
import com.example.consumablelife.core.data.OdoSource
import com.example.consumablelife.core.designsystem.ActionButton
import com.example.consumablelife.core.designsystem.AppCard
import com.example.consumablelife.core.designsystem.Dot
import com.example.consumablelife.core.designsystem.IconTile
import com.example.consumablelife.core.designsystem.ItemIcons
import com.example.consumablelife.core.designsystem.LocalAppColors
import com.example.consumablelife.core.designsystem.LocalMotion
import com.example.consumablelife.core.designsystem.LocalUi
import com.example.consumablelife.core.designsystem.PlayedAnimations
import com.example.consumablelife.core.designsystem.Seg
import com.example.consumablelife.core.designsystem.Segmented
import com.example.consumablelife.core.designsystem.StatusChip
import com.example.consumablelife.core.designsystem.Tokens
import com.example.consumablelife.core.designsystem.Ui
import com.example.consumablelife.core.designsystem.diagram.DiagramSpec
import com.example.consumablelife.core.designsystem.diagram.rememberDiagramSpec
import com.example.consumablelife.core.domain.DiagramView
import com.example.consumablelife.core.domain.ReplacementRecord
import com.example.consumablelife.core.domain.Status
import com.example.consumablelife.core.notify.StatusNotifier
import com.example.consumablelife.feature.detail.DetailContent
import com.example.consumablelife.feature.detail.IntervalDialog
import com.example.consumablelife.feature.detail.ItemActionsViewModel
import com.example.consumablelife.feature.detail.RecordEditDialog
import com.example.consumablelife.feature.detail.RefillRequest
import com.example.consumablelife.ui.Bucket
import com.example.consumablelife.ui.ItemUi
import com.example.consumablelife.ui.StatusFilter
import com.example.consumablelife.ui.diagramCyclesOf
import com.example.consumablelife.ui.distance
import com.example.consumablelife.ui.itemName
import com.example.consumablelife.ui.itemUisOf
import com.example.consumablelife.ui.kindLabel
import com.example.consumablelife.ui.rememberCtx
import com.example.consumablelife.ui.statusLabel
import com.example.consumablelife.ui.verb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/* 설계서 v3 3장 — 화면 구조: AppHeader · Tab(차량 | 대시보드 | 타임라인) · RecordCta · Sheet(상세) · S5 주행 중 */

enum class MainTab { VEHICLE, DASHBOARD, TIMELINE }

/** 기록 저장 직후 탭 화면으로 돌아올 때 넘어오는 값 (리필 애니메이션 + 실행 취소) */
data class SavedRecord(val itemId: String, val recordId: Long, val fromProgress: Float, val fromStatus: String, val fromCycleIndex: Int)

@HiltViewModel
class ConsumablesViewModel @Inject constructor(
    repo: ConsumableRepository,
    notifier: StatusNotifier,
) : ItemActionsViewModel(repo) {
    /** foreground 구독 중에만 상태 변화 알림 판단 (FR-N01, NFR-04) */
    val data: StateFlow<DashboardData?> = repo.dashboard
        .onEach { notifier.onStates(it.items) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // NV-01·02 / IM-01: 탭·선택·필터는 세 탭이 공유하는 단일 상태. 프로세스가 살아 있는 동안만 기억 → 재실행 시 차량 탭
    var tab by mutableStateOf(MainTab.VEHICLE)
    /** null = 선택 없음 → 가장 급한 항목을 보여 준다 (DB-05) */
    var selectedId by mutableStateOf<String?>(null)
    var filter by mutableStateOf(StatusFilter.ALL)

    /** HS-08: 투시/실물 선택은 DataStore 저장 */
    fun setView(v: DiagramView) = viewModelScope.launch { repo.updateSettings { it.copy(diagramView = v) } }
}

/** 탭 본문이 쓰는 공통 인자 묶음 */
internal class TabScope(
    val data: DashboardData,
    val spec: DiagramSpec,
    val items: List<ItemUi>,
    /** 선택 항목이 없으면 가장 급한 항목 (DB-05) */
    val current: ItemUi?,
    val urgent: ItemUi?,
    val selectedId: String?,
    val filter: StatusFilter,
    val counts: Map<Bucket, Int>,
    /** 창이 좁을 때(Medium 이하): 세로로 쌓는 축약 레이아웃 (13장) */
    val compact: Boolean,
    val onSelect: (String?) -> Unit,
    val onFilter: (StatusFilter) -> Unit,
    val onView: (DiagramView) -> Unit,
    val onDetail: (String) -> Unit,
    val onEditInterval: (String) -> Unit,
    val onRecord: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumablesScreen(
    saved: SavedRecord?,
    onSavedConsumed: () -> Unit,
    onRecord: (String) -> Unit,
    onSettings: () -> Unit,
    vm: ConsumablesViewModel = hiltViewModel(),
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val ctx = rememberCtx()
    val spec = rememberDiagramSpec()
    val app = LocalAppColors.current
    val driving = LocalUx.current.requiresDistractionOptimization
    val snackbar = remember { SnackbarHostState() }

    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var detailFor by rememberSaveable { mutableStateOf<String?>(null) }   // Sheet DETAIL (깊이 2)
    var refill by remember { mutableStateOf<RefillRequest?>(null) }
    var editIntervalFor by rememberSaveable { mutableStateOf<String?>(null) }
    var editRecord by remember { mutableStateOf<ReplacementRecord?>(null) }

    val d = data
    val items = remember(d, spec) { d?.let { itemUisOf(ctx, it, spec) }.orEmpty() }
    val urgent = items.firstOrNull()
    val current = items.firstOrNull { it.id == vm.selectedId } ?: urgent
    val counts = remember(items) { Bucket.entries.associateWith { b -> items.count { it.bucket == b } } }

    // FR-R04: 기록 저장 직후 — 그 항목을 선택하고 상세 시트에서 리필(AN-07) 재생 + 실행 취소 스낵바 5초.
    // 스낵바는 화면 수명(scope)에 묶는다: 저장 결과(saved)를 소비해 null이 돼도 5초 동안 살아 있어야 한다.
    val scope = rememberCoroutineScope()
    LaunchedEffect(saved?.recordId, d != null) {
        val sv = saved ?: return@LaunchedEffect
        val st = d?.items?.firstOrNull { it.item.id == sv.itemId } ?: return@LaunchedEffect
        onSavedConsumed()
        val key = "undo:${sv.recordId}"
        if (PlayedAnimations.hasPlayed(key)) return@LaunchedEffect
        PlayedAnimations.markPlayed(key)
        vm.selectedId = sv.itemId
        refill = RefillRequest(
            sv.recordId, sv.fromProgress.coerceAtLeast(0f),
            runCatching { Status.valueOf(sv.fromStatus) }.getOrDefault(Status.UNKNOWN), sv.fromCycleIndex,
        )
        detailFor = sv.itemId
        val savedText = ctx.getString(R.string.detail_saved, verb(ctx, st.kind))
        val undoText = ctx.getString(R.string.detail_undo)
        scope.launch {
            val result = withTimeoutOrNull(5_000) {
                snackbar.showSnackbar(savedText, actionLabel = undoText, withDismissAction = false, duration = SnackbarDuration.Indefinite)
            }
            if (result == null) snackbar.currentSnackbarData?.dismiss()
            if (result == SnackbarResult.ActionPerformed) vm.deleteRecord(sv.recordId)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        // 13장 반응형: Expanded는 설계서 그대로(창에 맞춰 비율 축소), 그보다 좁으면 세로로 쌓는 축약 레이아웃
        val compact = maxWidth < COMPACT_BELOW
        val ui = remember(maxWidth, maxHeight, compact) {
            if (compact) Ui((maxWidth.value / 1100f).coerceIn(0.62f, 0.85f)) else Ui.forWindow(maxWidth.value, maxHeight.value)
        }
        val showSummary = maxWidth >= 1400.dp
        CompositionLocalProvider(LocalUi provides ui) {
            if (d == null) return@CompositionLocalProvider
            if (driving) {
                // 11장 S5: 탭바·요약·CTA·도크 숨김, 히어로 카드 1장만 (선택 무시, 터치 무반응)
                DrivingView(urgent, spec, d)
                return@CompositionLocalProvider
            }
            val scope = TabScope(
                data = d, spec = spec, items = items, current = current, urgent = urgent,
                selectedId = vm.selectedId, filter = vm.filter, counts = counts, compact = compact,
                onSelect = { vm.selectedId = it }, onFilter = { vm.filter = it }, onView = vm::setView,
                onDetail = { refill = null; detailFor = it },
                onEditInterval = { editIntervalFor = it },
                onRecord = onRecord,
            )
            Column(Modifier.fillMaxSize().padding(ui.dp(20))) {
                AppHeader(
                    d = d, spec = spec, tab = vm.tab, counts = counts, showSummary = showSummary, compact = compact,
                    onTab = { vm.tab = it },
                    onSummary = { f -> vm.tab = MainTab.VEHICLE; vm.filter = f },
                    onSettings = onSettings,
                )
                Spacer(Modifier.height(ui.dp(12)))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (items.isEmpty()) {
                        Text(stringResource(R.string.dash_empty), style = ui.bodyL, color = app.text2)
                    } else {
                        // AN-01 탭 전환: 페이드 + 8dp 위로 슬라이드, 200ms. 좌우 스와이프 페이징 없음 (NV-03)
                        val motion = LocalMotion.current
                        val shift = with(LocalDensity.current) { 8.dp.roundToPx() }
                        AnimatedContent(
                            targetState = vm.tab,
                            transitionSpec = {
                                if (motion.full) (fadeIn(tween(200)) + slideInVertically(tween(200)) { shift }) togetherWith fadeOut(tween(120))
                                else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            },
                            label = "tab",
                        ) { tab ->
                            when (tab) {
                                MainTab.VEHICLE -> VehicleTab(scope)
                                MainTab.DASHBOARD -> DashboardTab(scope)
                                MainTab.TIMELINE -> TimelineTab(scope)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(ui.dp(12)))
                // RecordCta: 모든 탭 공통 위치의 주 버튼 1개 (NV-07 선택 항목이 있으면 항목명을 품는다)
                val picked = items.firstOrNull { it.id == vm.selectedId }
                ActionButton(
                    text = picked?.let { stringResource(R.string.cta_record_item, it.name, it.verb) } ?: stringResource(R.string.dash_record_cta),
                    onClick = { if (picked != null) onRecord(picked.id) else pickerOpen = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ui.dp(52)),
                    icon = Icons.Filled.TaskAlt, enabled = items.isNotEmpty(),
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    // ── 깊이 2: 상세 시트 (10장) ──
    val detailState = d?.items?.firstOrNull { it.item.id == detailFor }
    if (d != null && detailState != null && !driving) {
        DetailSheet(
            d = d, state = detailState, refill = refill, snackbar = snackbar,
            onDismiss = { detailFor = null; refill = null },
            onEditInterval = { editIntervalFor = detailState.item.id },
            onRecord = { detailFor = null; onRecord(detailState.item.id) },
            onEditRecord = { editRecord = it },
            onDeleteRecord = { vm.deleteRecord(it.id) },
        )
    }
    if (d != null) {
        d.items.firstOrNull { it.item.id == editIntervalFor }?.let { st ->
            IntervalDialog(st.item, d.settings.drivingCondition, { editIntervalFor = null }) { vm.saveItem(it); editIntervalFor = null }
        }
        editRecord?.let { rec ->
            RecordEditDialog(rec, d.odometer.km, { editRecord = null }) { vm.updateRecord(it); editRecord = null }
        }
        if (pickerOpen) {
            ItemPickerSheet(d.items, onDismiss = { pickerOpen = false }) { id -> pickerOpen = false; onRecord(id) }
        }
    }
}

private val COMPACT_BELOW = 1100.dp

/** 6장 AppHeader: 좌 캡션 + 주행거리 / 중 탭바 / 우 요약 카운터 + 설정 */
@Composable
private fun AppHeader(
    d: DashboardData,
    spec: DiagramSpec,
    tab: MainTab,
    counts: Map<Bucket, Int>,
    showSummary: Boolean,
    compact: Boolean,
    onTab: (MainTab) -> Unit,
    onSummary: (StatusFilter) -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = rememberCtx()
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val action = (counts[Bucket.DUE] ?: 0) + (counts[Bucket.CAUTION] ?: 0)
    val tabs = listOf(
        // NV-05 차량 탭 라벨에만 '조치 필요' 건수 뱃지 (0이면 숨김)
        Seg(stringResource(R.string.tab_vehicle), icon = Icons.Filled.DirectionsCar, badge = action.takeIf { it > 0 }?.toString()),
        Seg(stringResource(R.string.tab_dashboard), icon = Icons.Filled.GridView),
        Seg(stringResource(R.string.tab_timeline), icon = Icons.Filled.Timeline),
    )
    val odometer: @Composable () -> Unit = {
        Column {
            Text(
                stringResource(R.string.odo_caption, spec.vehicle.ifBlank { stringResource(R.string.app_name) }),
                style = ui.caption, color = app.text3, maxLines = 1,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    distance(ctx, d.odometer.km, d.settings.distanceUnit, round = false), style = ui.display, color = app.text, maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = "odometer" },
                )
                Spacer(Modifier.width(ui.dp(6)))
                Text(
                    when (d.odometer.source) {
                        OdoSource.MANUAL -> stringResource(R.string.dash_odometer_manual)
                        OdoSource.LAST_SAVED -> stringResource(R.string.dash_odometer_saved)
                        else -> stringResource(R.string.odo_total)
                    },
                    style = ui.label, color = app.text2, maxLines = 1, modifier = Modifier.padding(bottom = ui.dp(4)),
                )
            }
        }
    }
    val settings: @Composable () -> Unit = {
        IconTile(Icons.Outlined.Settings, stringResource(R.string.dash_settings), onSettings)
    }
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(ui.dp(10))) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { odometer() }
                settings()
            }
            Segmented(tabs, tab.ordinal, { onTab(MainTab.entries[it]) }, tall = true)
        }
        return
    }
    Row(Modifier.fillMaxWidth().height(ui.dp(64)), verticalAlignment = Alignment.CenterVertically) {
        odometer()
        Spacer(Modifier.width(ui.dp(26)))
        Segmented(tabs, tab.ordinal, { onTab(MainTab.entries[it]) }, tall = true)
        Spacer(Modifier.weight(1f))
        if (showSummary) {
            // SummaryCounter: 탭하면 차량 탭 + 해당 필터
            AppCard(Modifier.height(ui.dp(40))) {
                Row(Modifier.fillMaxHeight().padding(horizontal = ui.dp(6)), verticalAlignment = Alignment.CenterVertically) {
                    SummaryCell(app.status.due, stringResource(R.string.sum_due), counts[Bucket.DUE] ?: 0) { onSummary(StatusFilter.DUE) }
                    SummaryCell(app.status.caution, stringResource(R.string.sum_caution), counts[Bucket.CAUTION] ?: 0) { onSummary(StatusFilter.CAUTION) }
                    SummaryCell(app.status.good, stringResource(R.string.sum_good), counts[Bucket.GOOD] ?: 0) { onSummary(StatusFilter.GOOD) }
                }
            }
            Spacer(Modifier.width(ui.dp(10)))
        }
        settings()
    }
}

@Composable
private fun SummaryCell(color: androidx.compose.ui.graphics.Color, label: String, n: Int, onClick: () -> Unit) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    Row(
        Modifier.fillMaxHeight().clip(RoundedCornerShape(ui.dp(8))).clickable(onClick = onClick).padding(horizontal = ui.dp(8)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(6)),
    ) {
        Dot(color)
        Text(label, style = ui.label, color = app.text2, maxLines = 1)
        Text("$n", style = ui.labelBold.copy(fontWeight = FontWeight.Bold), color = app.text, maxLines = 1)
    }
}

/** 10장 상세 시트: 요약(라이프 루프·미니 도식) · 주기 정보 · 사이클 이력 · 교체/점검 이력 · 하단 버튼 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailSheet(
    d: DashboardData,
    state: ConsumableState,
    refill: RefillRequest?,
    /** 시트는 별도 창이라 화면의 스낵바를 가린다 → 실행 취소 스낵바를 시트 안에서도 띄운다 */
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit,
    onEditInterval: () -> Unit,
    onRecord: () -> Unit,
    onEditRecord: (ReplacementRecord) -> Unit,
    onDeleteRecord: (ReplacementRecord) -> Unit,
) {
    val ctx = rememberCtx()
    val app = LocalAppColors.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheet,
        sheetMaxWidth = Dp.Unspecified, containerColor = app.bgApp, scrimColor = app.scrim, // DS-03 스크림 40% / 24%
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 1000.dp
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.92f).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        itemName(ctx, state.item), style = MaterialTheme.typography.headlineSmall, color = app.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    StatusChip(state.displayStatus, statusLabel(ctx, state.displayStatus, state.kind))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(Tokens.TouchTarget)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.close), Modifier.size(32.dp))
                    }
                }
                DetailContent(
                    state = state, settings = d.settings,
                    cycles = diagramCyclesOf(ctx, d.items, d.settings.distanceUnit), tires = d.tires,
                    odoNowKm = d.odometer.km, today = d.today, refill = refill,
                    onEditInterval = onEditInterval, onRecord = onRecord,
                    onEditRecord = onEditRecord, onDeleteRecord = onDeleteRecord,
                    wide = wide, data = d,
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        }
    }
}

/** RecordCta에서 선택 항목이 없을 때: 어떤 항목을 기록할지 고르는 시트 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemPickerSheet(items: List<ConsumableState>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = rememberCtx()
    val app = LocalAppColors.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = app.surface, scrimColor = app.scrim) {
        Text(
            stringResource(R.string.dash_pick_item), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(items, key = { it.item.id }) { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Tokens.TouchTarget)
                        .clickable { onPick(s.item.id) }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(ItemIcons.of(s.item.iconKey), null, Modifier.size(32.dp), tint = app.text2)
                    Column(Modifier.weight(1f)) {
                        Text(itemName(ctx, s.item), style = MaterialTheme.typography.titleSmall)
                        Text(kindLabel(ctx, s.kind), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(s.displayStatus, statusLabel(ctx, s.displayStatus, s.kind))
                }
            }
        }
    }
}

/** 11장 S5 주행 중: 가장 급한 항목 히어로 카드 1장 + 캡션. 버튼·애니메이션 없음, 모든 터치 무시 */
@Composable
private fun DrivingView(urgent: ItemUi?, spec: DiagramSpec, d: DashboardData) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    Column(
        Modifier.fillMaxSize().padding(ui.dp(20)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (urgent == null) {
            Text(stringResource(R.string.diag_all_good), style = ui.headline, color = app.text)
        } else {
            HeroCard(
                item = urgent, spec = spec, tires = d.tires, view = DiagramView.EXTERIOR, still = true,
                subLine = stringResource(R.string.s5_sub),
                modifier = Modifier.width(ui.dp(900)).height(ui.dp(170)),
                ringSize = ui.dp(120), miniWidth = ui.dp(220),
            )
        }
        Spacer(Modifier.height(ui.dp(18)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ui.dp(8))) {
            Icon(Icons.Filled.Lock, null, tint = app.text3, modifier = Modifier.size(ui.dp(18)))
            Text(stringResource(R.string.s5_note), style = ui.label, color = app.text3)
        }
    }
}
