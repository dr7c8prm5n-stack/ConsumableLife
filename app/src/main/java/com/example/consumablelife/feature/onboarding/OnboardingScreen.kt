package com.example.consumablelife.feature.onboarding

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.consumablelife.core.data.ConsumableRepository
import com.example.consumablelife.core.data.ConsumableRepository.InitialRecord
import com.example.consumablelife.core.data.OdoSource
import com.example.consumablelife.core.data.OdometerState
import com.example.consumablelife.core.designsystem.BigButton
import com.example.consumablelife.core.designsystem.BrandTitle
import com.example.consumablelife.core.designsystem.ChoiceRow
import com.example.consumablelife.core.designsystem.DateField
import com.example.consumablelife.core.designsystem.IconBadge
import com.example.consumablelife.core.designsystem.ItemIcons
import com.example.consumablelife.core.designsystem.SectionCard
import com.example.consumablelife.core.designsystem.Stepper
import com.example.consumablelife.core.designsystem.Tokens
import com.example.consumablelife.core.designsystem.formatNumber
import com.example.consumablelife.core.domain.Condition
import com.example.consumablelife.core.domain.ConsumableItem
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.Powertrain
import com.example.consumablelife.core.domain.RecordAction
import com.example.consumablelife.core.domain.RecordSource
import com.example.consumablelife.core.vehicle.VehicleCapabilities
import com.example.consumablelife.core.vehicle.VehicleDataSource
import com.example.consumablelife.ui.conditionLabel
import com.example.consumablelife.ui.distance
import com.example.consumablelife.ui.itemName
import com.example.consumablelife.ui.kindLabel
import com.example.consumablelife.ui.powertrainLabel
import com.example.consumablelife.ui.rememberCtx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

enum class LastChoice { RECENT, INPUT, UNKNOWN }

private fun actionOf(item: ConsumableItem) = if (item.cycleKind == CycleKind.INSPECT) RecordAction.INSPECTED else RecordAction.REPLACED

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repo: ConsumableRepository,
    vehicle: VehicleDataSource,
) : ViewModel() {
    val odometer: StateFlow<OdometerState?> = repo.odometer.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val capabilities: StateFlow<VehicleCapabilities> = vehicle.capabilities
    val presets = MutableStateFlow<List<ConsumableItem>>(emptyList())

    /** 온보딩 저장 완료 여부 — 화면 전환은 콜백이 아니라 이 저장 상태를 보고 한다 (액티비티 재생성·권한 다이얼로그에 영향받지 않음) */
    val done: StateFlow<Boolean> = repo.settings.map { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 저장 실패 메시지 (null = 정상). 실패 시 시작 버튼을 다시 활성화한다 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private fun complete(block: suspend () -> Unit) = viewModelScope.launch {
        _error.value = null
        runCatching { block() }.onFailure {
            Log.e(TAG, "onboarding finish failed", it)
            _error.value = it.message ?: it.javaClass.simpleName
        }
    }

    init {
        viewModelScope.launch { presets.value = repo.presetItems() }
    }

    fun finish(
        powertrain: Powertrain, condition: Condition, odoKm: Double, manualMode: Boolean,
        choices: Map<String, Triple<LastChoice, Long, Int>>, vehicleOdo: Boolean,
    ) = complete {
        val today = LocalDate.now()
        val recs = presets.value.filter { it.appliesTo(powertrain) }.map { item ->
            val (c, epochDay, odo) = choices[item.id] ?: Triple(LastChoice.UNKNOWN, today.toEpochDay(), odoKm.roundToInt())
            val action = actionOf(item)
            when (c) {
                LastChoice.RECENT -> InitialRecord(item.id, odoKm, today, if (vehicleOdo) RecordSource.VEHICLE_ODO else RecordSource.MANUAL, action)
                LastChoice.INPUT -> InitialRecord(item.id, odo.toDouble().coerceAtMost(odoKm), LocalDate.ofEpochDay(epochDay), RecordSource.MANUAL, action)
                LastChoice.UNKNOWN -> InitialRecord(item.id, odoKm, today, RecordSource.ESTIMATED, action)
            }
        }
        repo.completeOnboarding(
            powertrain, condition, if (manualMode) odoKm else null, recs,
            seedDemoSnapshots = BuildConfig.FLAVOR == "mock",
        )
    }

    /**
     * mock flavor 데모: 명세 v2.0 시안의 데모 값(누적 48,213 km)과 같은 상태 분포 —
     * 와이퍼 초과, 12V 배터리·타이어 마모 교체 시기, 에어컨 필터·감속기 오일 곧 교체/점검, 나머지 양호.
     * 출고 후 한 번도 손대지 않은 품목은 출고(29개월 전, 0 km) 기준 추정 기록.
     */
    fun startDemo(odoKm: Double) = complete {
        val t = LocalDate.now()
        fun r(id: String, usedKm: Double, monthsAgo: Long, daysAgo: Long = 0, action: RecordAction = RecordAction.REPLACED) =
            InitialRecord(id, (odoKm - usedKm).coerceAtLeast(0.0), t.minusMonths(monthsAgo).minusDays(daysAgo), RecordSource.MANUAL, action)
        fun factory(id: String) = InitialRecord(id, 0.0, t.minusMonths(29), RecordSource.ESTIMATED)
        repo.completeOnboarding(
            Powertrain.EV, Condition.NORMAL, null,
            listOf(
                r("BATTERY_12V", 30_000.0, 44),                                   // 48개월 중 44 → 92% DUE, 2회차
                r("WIPER", 8_500.0, 12, daysAgo = 15), r("WIPER", 17_500.0, 25),  // 12.5/12개월 → 104% OVERDUE(+2주), 3회차
                r("CABIN_FILTER", 11_850.0, 9), r("CABIN_FILTER", 27_250.0, 20),  // 11,850/15,000 → 79% CAUTION, 3회차
                r("HV_BATTERY", 4_000.0, 6, action = RecordAction.INSPECTED),     // 점검 6/12개월 → 50% GOOD, 3회차
                r("HV_BATTERY", 12_000.0, 18, action = RecordAction.INSPECTED),
                r("TIRE_ROTATION", 4_680.0, 4), r("TIRE_ROTATION", 18_080.0, 14), r("TIRE_ROTATION", 31_480.0, 25), // 36% GOOD, 4회차
                factory("COOLANT"),       // 최초 200,000 km/120개월 → 24%, "최초 교체까지 약 7.6년"
                factory("BRAKE_PAD"),     // 48,213/80,000 → 60% GOOD
                factory("BRAKE_FLUID"),   // 48,213/78,000 → 62% GOOD
                factory("REDUCER_OIL"),   // 48,213/60,000 → 80% CAUTION (점검)
                factory("TIRE_REPLACE"),  // 48,213/50,000 → 96% DUE
            ),
            seedDemoSnapshots = true,
        )
    }

    private companion object { const val TAG = "Cartalog" }
}

@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val ctx = rememberCtx()
    val odoState by vm.odometer.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    var step by rememberSaveable { mutableStateOf(0) }
    var powertrain by rememberSaveable { mutableStateOf<Powertrain?>(Powertrain.EV) } // 아이오닉 5 프로필 기본값
    val condition = Condition.NORMAL // 운행 조건은 온보딩에서 묻지 않고 설정에서 변경
    var manualOdo by rememberSaveable { mutableStateOf<Int?>(null) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    // itemId → (선택, 날짜 epochDay, 주행거리)
    val choices = rememberSaveable(saver = choiceSaver()) { mutableStateMapOf<String, Triple<LastChoice, Long, Int>>() }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // 저장이 끝나면(onboardingDone=true) 대시보드로. 저장 실패 시 버튼을 되살려 다시 시도할 수 있게 한다.
    LaunchedEffect(done) { if (done) onDone() }
    LaunchedEffect(error) { if (error != null) finishing = false }

    val manualMode = caps.resolved && (!caps.odometer || caps.permissionDenied) || odoState?.source == OdoSource.MANUAL || odoState?.source == OdoSource.NONE
    val vehicleKm = odoState?.takeIf { it.source == OdoSource.VEHICLE || it.source == OdoSource.LAST_SAVED }?.km
    LaunchedEffect(manualMode, vehicleKm) {
        if (manualOdo == null) manualOdo = (vehicleKm ?: 0.0).roundToInt()
    }
    val odoKm = if (manualMode) (manualOdo ?: 0).toDouble() else vehicleKm ?: 0.0
    val total = 4

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(Tokens.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandTitle(
                    stringResource(R.string.app_name), Modifier.weight(1f),
                    caption = stringResource(R.string.brand_caption), titleStyle = MaterialTheme.typography.titleLarge,
                )
                Text(stringResource(R.string.onb_step, step + 1, total), style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(progress = { (step + 1) / total.toFloat() }, modifier = Modifier.fillMaxWidth())

            when (step) {
                0 -> SectionCard {
                    Icon(Icons.Filled.DirectionsCar, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.onb_welcome), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.onb_vehicle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (manualMode) {
                        Text(stringResource(R.string.onb_odo_manual), style = MaterialTheme.typography.bodyMedium)
                        Stepper(
                            stringResource(R.string.fmt_km, formatNumber(manualOdo ?: 0)), listOf(100, 1_000, 10_000),
                            { d -> manualOdo = ((manualOdo ?: 0) + d).coerceIn(0, 999_999) },
                        )
                    } else {
                        Text(distance(ctx, odoKm, com.example.consumablelife.core.domain.DistanceUnit.KM, round = false), style = MaterialTheme.typography.displayMedium)
                    }
                    if (BuildConfig.FLAVOR == "mock") {
                        BigButton(
                            "데모 데이터로 바로 시작 (mock · 아이오닉 5)",
                            onClick = { if (!finishing) { finishing = true; vm.startDemo(odoKm) } },
                            modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Science, primary = false,
                        )
                    }
                }
                1 -> SectionCard {
                    Text(stringResource(R.string.onb_powertrain), style = MaterialTheme.typography.headlineSmall)
                    // 3개 큰 버튼
                    Powertrain.entries.forEach { p ->
                        BigButton(
                            powertrainLabel(ctx, p), { powertrain = p },
                            Modifier.fillMaxWidth(), primary = powertrain == p,
                        )
                    }
                }
                2 -> {
                    Text(stringResource(R.string.onb_last), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.onb_unknown_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    presets.filter { it.appliesTo(powertrain) && !it.activeInterval(condition, 1).isEmpty }.forEach { item ->
                        val cur = choices[item.id] ?: Triple(LastChoice.UNKNOWN, LocalDate.now().toEpochDay(), odoKm.roundToInt())
                        SectionCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconBadge(ItemIcons.of(item.iconKey), size = 40.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(itemName(ctx, item), style = MaterialTheme.typography.titleMedium)
                                    Text(kindLabel(ctx, item.activeKind(condition)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            ChoiceRow(
                                LastChoice.entries, cur.first,
                                {
                                    stringResource(
                                        when (it) {
                                            LastChoice.RECENT -> R.string.onb_opt_recent
                                            LastChoice.INPUT -> R.string.onb_opt_input
                                            LastChoice.UNKNOWN -> R.string.onb_opt_unknown
                                        },
                                    )
                                },
                                { choices[item.id] = cur.copy(first = it) },
                            )
                            if (cur.first == LastChoice.INPUT) {
                                DateField(
                                    stringResource(R.string.rec_date), LocalDate.ofEpochDay(cur.second),
                                    { choices[item.id] = cur.copy(second = it.toEpochDay()) }, LocalDate.now(),
                                )
                                Stepper(
                                    stringResource(R.string.fmt_km, formatNumber(cur.third)), listOf(1_000, 10_000),
                                    { d -> choices[item.id] = cur.copy(third = (cur.third + d).coerceIn(0, odoKm.roundToInt())) },
                                )
                            }
                        }
                    }
                }
                else -> SectionCard {
                    Text(stringResource(R.string.onb_summary), style = MaterialTheme.typography.headlineSmall)
                    val count = presets.count { it.appliesTo(powertrain) && !it.activeInterval(condition, 1).isEmpty }
                    Text(
                        stringResource(
                            R.string.onb_summary_desc,
                            powertrain?.let { powertrainLabel(ctx, it) } ?: "",
                            conditionLabel(ctx, condition), count,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let { Text(stringResource(R.string.onb_save_failed, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
                if (step > 0) {
                    BigButton(stringResource(R.string.back), { step-- }, Modifier.weight(1f), primary = false)
                }
                val canNext = when (step) {
                    1 -> powertrain != null
                    2 -> presets.isNotEmpty()
                    else -> true
                }
                if (step < total - 1) {
                    BigButton(stringResource(R.string.next), { step++ }, Modifier.weight(1f), enabled = canNext)
                } else {
                    BigButton(
                        stringResource(R.string.onb_start),
                        onClick = {
                            if (finishing) return@BigButton
                            finishing = true
                            if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            vm.finish(
                                powertrain ?: Powertrain.EV, condition, odoKm, manualMode, choices.toMap(),
                                vehicleOdo = !manualMode,
                            )
                        },
                        modifier = Modifier.weight(1f), enabled = !finishing,
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

private fun choiceSaver() = androidx.compose.runtime.saveable.Saver<
    androidx.compose.runtime.snapshots.SnapshotStateMap<String, Triple<LastChoice, Long, Int>>, List<String>,
    >(
    save = { m -> m.map { (k, v) -> "$k|${v.first.name}|${v.second}|${v.third}" } },
    restore = { l ->
        mutableStateMapOf<String, Triple<LastChoice, Long, Int>>().apply {
            l.forEach { s ->
                val p = s.split('|')
                put(p[0], Triple(LastChoice.valueOf(p[1]), p[2].toLong(), p[3].toInt()))
            }
        }
    },
)
