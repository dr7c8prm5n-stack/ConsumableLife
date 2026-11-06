package com.example.consumablelife.feature.settings

import com.example.consumablelife.core.designsystem.BrandTitle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.consumablelife.BuildConfig
import com.example.consumablelife.R
import com.example.consumablelife.app.LocalUx
import com.example.consumablelife.core.data.AppSettings
import com.example.consumablelife.core.data.ConsumableRepository
import com.example.consumablelife.core.data.DashboardData
import com.example.consumablelife.core.designsystem.ChoiceRow
import com.example.consumablelife.core.designsystem.ItemIcons
import com.example.consumablelife.core.designsystem.SectionCard
import com.example.consumablelife.core.designsystem.Stepper
import com.example.consumablelife.core.designsystem.Tokens
import com.example.consumablelife.core.designsystem.formatNumber
import com.example.consumablelife.core.domain.Condition
import com.example.consumablelife.core.domain.ConsumableItem
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.DistanceUnit
import com.example.consumablelife.core.domain.Interval
import com.example.consumablelife.core.domain.Powertrain
import com.example.consumablelife.core.vehicle.ApiState
import com.example.consumablelife.core.vehicle.FakeScenario
import com.example.consumablelife.core.vehicle.VehicleApi
import com.example.consumablelife.core.vehicle.UxRestrictionMonitor
import com.example.consumablelife.core.vehicle.VehicleDataSource
import com.example.consumablelife.feature.detail.IntervalDialog
import com.example.consumablelife.feature.detail.intervalText
import com.example.consumablelife.ui.apiLabel
import com.example.consumablelife.ui.apiStateLabel
import com.example.consumablelife.ui.conditionLabel
import com.example.consumablelife.ui.distance
import com.example.consumablelife.ui.itemName
import com.example.consumablelife.ui.kindLabel
import com.example.consumablelife.ui.powertrainLabel
import com.example.consumablelife.ui.rememberCtx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: ConsumableRepository,
    private val vehicle: VehicleDataSource,
    val uxMonitor: UxRestrictionMonitor,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val items: StateFlow<List<ConsumableItem>> = repo.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** 차량 연동 상태 카드용 — 구독하는 동안 차량 값(누적 트립 등)이 계속 갱신된다 */
    val dashboard: StateFlow<DashboardData?> = repo.dashboard.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val debug = vehicle.debugControls

    fun update(t: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.updateSettings(t) }
    fun saveItem(i: ConsumableItem) = viewModelScope.launch { repo.updateItem(i) }
    fun addCustom(name: String) = viewModelScope.launch { repo.addCustomItem(name, Interval(10_000, 12)) }
    fun deleteCustom(id: String) = viewModelScope.launch { repo.deleteCustomItem(id) }
    fun reset(onDone: () -> Unit) = viewModelScope.launch { repo.resetAll(); onDone() }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onResetDone: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val ctx = rememberCtx()
    val s by vm.settings.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val ux = LocalUx.current
    val locked = ux.noSetup // FR-D01: 설정 화면 활성 조건
    var editItem by rememberSaveable { mutableStateOf<String?>(null) }
    var reset by rememberSaveable { mutableStateOf(0) }
    var customName by rememberSaveable { mutableStateOf("") }
    val settings = s ?: return

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Gutter, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(Tokens.TouchTarget)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), Modifier.size(36.dp))
                }
                BrandTitle(stringResource(R.string.set_title), titleStyle = MaterialTheme.typography.headlineSmall)
            }
            if (locked) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Lock, null)
                    Text(stringResource(R.string.set_locked_driving), style = MaterialTheme.typography.titleSmall)
                }
            }

            SectionCard {
                Text(stringResource(R.string.set_condition), style = MaterialTheme.typography.titleMedium)
                ChoiceRow(listOf(Condition.NORMAL, Condition.SEVERE), settings.drivingCondition, { conditionLabel(ctx, it) },
                    { c -> vm.update { it.copy(drivingCondition = c) } }, enabled = !locked)
                Text(stringResource(R.string.set_powertrain), style = MaterialTheme.typography.titleMedium)
                ChoiceRow(Powertrain.entries, settings.powertrain, { powertrainLabel(ctx, it) },
                    { p -> vm.update { it.copy(powertrain = p) } }, enabled = !locked)
            }

            SectionCard {
                Text(stringResource(R.string.set_items), style = MaterialTheme.typography.titleMedium)
                items.filter { it.appliesTo(settings.powertrain) }.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Tokens.TouchTarget)
                            .clickable(enabled = !locked) { editItem = item.id },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(ItemIcons.of(item.iconKey), null, Modifier.size(30.dp))
                        Column(Modifier.weight(1f)) {
                            Text(itemName(ctx, item), style = MaterialTheme.typography.titleSmall)
                            Text(
                                kindLabel(ctx, item.activeKind(settings.drivingCondition)) + " · " +
                                    intervalText(item.activeInterval(settings.drivingCondition, 1), settings.distanceUnit) +
                                    if (item.isUserDefault && item.cycleKind == CycleKind.USER) " · " + stringResource(R.string.user_default_badge) else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (item.isCustom) {
                            IconButton(onClick = { vm.deleteCustom(item.id) }, enabled = !locked, modifier = Modifier.size(Tokens.TouchTarget)) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.set_custom_delete))
                            }
                        }
                        Switch(item.enabled, { e -> vm.saveItem(item.copy(enabled = e)) }, enabled = !locked)
                    }
                }
                val customCount = items.count { it.isCustom }
                if (customCount < ConsumableItem.MAX_CUSTOM && !ux.noKeyboard) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            customName, { customName = it.take(16) }, Modifier.weight(1f),
                            label = { Text(stringResource(R.string.set_add_custom, customCount)) },
                            singleLine = true, enabled = !locked, textStyle = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(
                            onClick = { vm.addCustom(customName); customName = "" },
                            enabled = !locked && customName.isNotBlank(), modifier = Modifier.size(Tokens.TouchTarget),
                        ) { Icon(Icons.Filled.Add, null, Modifier.size(36.dp)) }
                    }
                }
            }

            SectionCard {
                Text(stringResource(R.string.set_thresholds), style = MaterialTheme.typography.titleMedium)
                LabeledStepper(stringResource(R.string.set_caution_ratio), "${(settings.cautionRatio * 100).roundToInt()}%", listOf(5), !locked) { d ->
                    vm.update { it.copy(cautionRatio = (it.cautionRatio + d / 100f).coerceIn(it.dueRatio + 0.05f, 0.9f)) }
                }
                LabeledStepper(stringResource(R.string.set_due_ratio), "${(settings.dueRatio * 100).roundToInt()}%", listOf(5), !locked) { d ->
                    vm.update { it.copy(dueRatio = (it.dueRatio + d / 100f).coerceIn(0f, it.cautionRatio - 0.05f)) }
                }
                LabeledStepper(stringResource(R.string.set_caution_km), formatNumber(settings.cautionKm), listOf(100, 500), !locked) { d ->
                    vm.update { it.copy(cautionKm = (it.cautionKm + d).coerceIn(0, 20_000)) }
                }
                LabeledStepper(stringResource(R.string.set_caution_days), "${settings.cautionDays}", listOf(5), !locked) { d ->
                    vm.update { it.copy(cautionDays = (it.cautionDays + d).coerceIn(0, 180)) }
                }
            }

            SectionCard {
                Text(stringResource(R.string.set_periodic_scan), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.set_periodic_scan_desc), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                    Switch(settings.periodicScan, { on -> vm.update { it.copy(periodicScan = on) } }, enabled = !locked)
                }
                Text(stringResource(R.string.set_unit), style = MaterialTheme.typography.titleMedium)
                ChoiceRow(DistanceUnit.entries, settings.distanceUnit, { if (it == DistanceUnit.KM) "km" else "mile" },
                    { u -> vm.update { it.copy(distanceUnit = u) } }, enabled = !locked)
                Text(stringResource(R.string.set_manual_odo), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.set_manual_odo_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val manual = (settings.manualOdometerKm ?: settings.lastVehicleOdometerKm ?: 0.0).roundToInt()
                Stepper(stringResource(R.string.fmt_km, formatNumber(manual)), listOf(100, 1_000), { d ->
                    vm.update { it.copy(manualOdometerKm = (manual + d).coerceAtLeast(0).toDouble()) }
                }, enabled = !locked)
            }

            // 차량 연동 상태: Pleos Vehicle API별 결과 + 누적 트립·일평균 주행
            val dash by vm.dashboard.collectAsStateWithLifecycle()
            dash?.let { d -> VehicleApiCard(d) }

            // FR-S06 앱 정보·업데이트 (마켓 자동 업데이트 미지원 대응)
            SectionCard {
                Text(stringResource(R.string.set_about), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.set_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.set_update_guide), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard {
                TextButton(onClick = { reset = 1 }, enabled = !locked, modifier = Modifier.heightIn(min = Tokens.TouchTarget)) {
                    Text(stringResource(R.string.set_reset), color = com.example.consumablelife.core.designsystem.LocalStatusColors.current.overdue, style = MaterialTheme.typography.titleSmall)
                }
            }

            // 개발자 옵션: mock 시나리오 + AC-P 주행 제한 강제 훅
            if (BuildConfig.DEBUG || vm.debug != null) {
                SectionCard {
                    Text(stringResource(R.string.set_dev), style = MaterialTheme.typography.titleMedium)
                    val forced by vm.uxMonitor.forceRestricted.collectAsStateWithLifecycle()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.set_dev_force_do), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(forced, { vm.uxMonitor.forceRestricted.value = it })
                    }
                    vm.debug?.let { dbg ->
                        val scenario by dbg.scenario.collectAsStateWithLifecycle()
                        Text(stringResource(R.string.set_dev_scenario), style = MaterialTheme.typography.bodyLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FakeScenario.entries.forEach { sc ->
                                FilterChip(
                                    selected = sc == scenario, onClick = { dbg.setScenario(sc) },
                                    label = { Text(sc.label, style = MaterialTheme.typography.labelMedium) },
                                    modifier = Modifier.heightIn(min = 56.dp),
                                )
                            }
                        }
                        TextButton(onClick = { dbg.addKm(500.0) }, modifier = Modifier.heightIn(min = Tokens.TouchTarget)) {
                            Text(stringResource(R.string.set_dev_add_km), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }

    editItem?.let { id ->
        items.firstOrNull { it.id == id }?.let { item ->
            IntervalDialog(item, settings.drivingCondition, { editItem = null }) { vm.saveItem(it); editItem = null }
        }
    }
    // 데이터 초기화 2단계 확인
    if (reset > 0) {
        AlertDialog(
            onDismissRequest = { reset = 0 },
            text = { Text(stringResource(if (reset == 1) R.string.set_reset_confirm1 else R.string.set_reset_confirm2), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { if (reset == 1) reset = 2 else { reset = 0; vm.reset(onResetDone) } }, Modifier.heightIn(min = 64.dp)) {
                    Text(stringResource(if (reset == 1) R.string.next else R.string.set_reset), color = com.example.consumablelife.core.designsystem.LocalStatusColors.current.overdue)
                }
            },
            dismissButton = { TextButton(onClick = { reset = 0 }, Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun VehicleApiCard(d: DashboardData) {
    val ctx = rememberCtx()
    val colors = com.example.consumablelife.core.designsystem.LocalStatusColors.current
    SectionCard {
        Text(stringResource(R.string.set_vehicle_api), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.set_vehicle_api_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 누적 트립 (Odometer.getTripCumulativeDrivingDistance / Time)
        d.live.tripCumulativeKm?.let { km ->
            val dist = distance(ctx, km, d.settings.distanceUnit, round = false)
            val min = d.live.tripCumulativeMinutes
            val avg = d.live.averageSpeedKmh
            ApiRow(
                stringResource(R.string.set_trip),
                if (min != null && avg != null) stringResource(R.string.set_trip_value, dist, stringResource(R.string.fmt_hours_min, min / 60, min % 60), "%.1f".format(avg))
                else stringResource(R.string.set_trip_distance_only, dist),
                null, null,
            )
        }
        ApiRow(
            stringResource(R.string.set_avg_daily),
            d.avgKmPerDay?.let { stringResource(R.string.set_avg_daily_value, distance(ctx, it, d.settings.distanceUnit, round = false)) }
                ?: stringResource(R.string.set_avg_daily_collecting),
            null, null,
        )
        VehicleApi.entries.forEach { api ->
            val r = d.apiStatus[api]
            ApiRow(
                apiLabel(ctx, api), api.api + " · " + api.permission,
                apiStateLabel(ctx, r?.state) + (r?.detail?.let { " · $it" } ?: ""),
                when (r?.state) {
                    ApiState.CONNECTED -> colors.good
                    ApiState.PERMISSION_DENIED, ApiState.FAILED -> colors.due
                    else -> colors.unknown
                },
            )
        }
    }
}

@Composable
private fun ApiRow(title: String, sub: String, state: String?, stateColor: androidx.compose.ui.graphics.Color?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (state != null) {
            Text(state, style = MaterialTheme.typography.labelMedium, color = stateColor ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun LabeledStepper(label: String, value: String, steps: List<Int>, enabled: Boolean, onStep: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Stepper(value, steps, onStep, enabled = enabled)
    }
}
