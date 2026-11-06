package com.example.consumablelife.core.data

import com.example.consumablelife.core.domain.CarZone
import com.example.consumablelife.core.domain.Condition
import com.example.consumablelife.core.domain.ConsumableItem
import com.example.consumablelife.core.domain.CycleCalculator
import com.example.consumablelife.core.domain.CycleInfo
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.Interval
import com.example.consumablelife.core.domain.LastReplacement
import com.example.consumablelife.core.domain.LifeCalculator
import com.example.consumablelife.core.domain.LifeInput
import com.example.consumablelife.core.domain.LifeResult
import com.example.consumablelife.core.domain.Powertrain
import com.example.consumablelife.core.domain.RecordAction
import com.example.consumablelife.core.domain.RecordSource
import com.example.consumablelife.core.domain.ReplacementRecord
import com.example.consumablelife.core.domain.SensorAlert
import com.example.consumablelife.core.domain.SensorLink
import com.example.consumablelife.core.domain.Status
import com.example.consumablelife.core.domain.WheelPos
import com.example.consumablelife.core.domain.effectiveStatus
import com.example.consumablelife.core.domain.sortedForDashboard
import com.example.consumablelife.core.vehicle.ApiResult
import com.example.consumablelife.core.vehicle.FluidAlerts
import com.example.consumablelife.core.vehicle.Telltale
import com.example.consumablelife.core.vehicle.VehicleApi
import com.example.consumablelife.core.vehicle.VehicleCapabilities
import com.example.consumablelife.core.vehicle.VehicleDataSource
import com.example.consumablelife.core.vehicle.VehicleLive
import com.example.consumablelife.core.vehicle.isLow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** NFR-04: 날짜 변경은 foreground 진입 시에만 갱신 (타이머 없음) */
@Singleton
class TodayProvider @Inject constructor() {
    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()
    fun refresh() { _today.value = LocalDate.now() }
}

enum class OdoSource { VEHICLE, LAST_SAVED, MANUAL, NONE }

data class OdometerState(val km: Double, val source: OdoSource)

/** 항목 1개의 계산 결과 (UI 모델의 원천) */
data class ConsumableState(
    val item: ConsumableItem,
    val interval: Interval,
    val usesFirstInterval: Boolean,
    val result: LifeResult,
    val sensorAlert: SensorAlert?,
    val lastRecord: ReplacementRecord?,
    val records: List<ReplacementRecord>,
    /** 부록 A 사용 사이클 (DG-01) */
    val cycle: CycleInfo,
    /** 운행 조건이 반영된 주기 종류 (감속기 오일: 가혹 시 REPLACE) */
    val kind: CycleKind,
) {
    val displayStatus: Status get() = effectiveStatus(result.status, sensorAlert)
    val estimated: Boolean get() = lastRecord?.source == RecordSource.ESTIMATED
}

data class DashboardData(
    val odometer: OdometerState,
    val items: List<ConsumableState>,
    val settings: AppSettings,
    val capabilities: VehicleCapabilities,
    val fluid: FluidAlerts,
    val tires: Map<WheelPos, Boolean>,
    val collectingData: Boolean,
    val today: LocalDate,
    /** 품목과 매칭해 보여 주는 차량 실측값 (누적 트립·충전량·공기압·경고등) */
    val live: VehicleLive = VehicleLive(),
    /** API별 연동 결과 */
    val apiStatus: Map<VehicleApi, ApiResult> = emptyMap(),
    /** 최근 30일 일평균 주행거리 (없으면 수집 중) */
    val avgKmPerDay: Double? = null,
) {
    val vehicleAlerts: List<SensorAlert> get() = items.mapNotNull { it.sensorAlert }.distinct()
    val manualOdometerMode: Boolean
        get() = capabilities.resolved && (!capabilities.odometer || capabilities.permissionDenied)
}

@Singleton
class ConsumableRepository @Inject constructor(
    private val dao: ConsumableDao,
    private val settingsStore: SettingsDataStore,
    private val presetLoader: PresetLoader,
    private val vehicle: VehicleDataSource,
    private val todayProvider: TodayProvider,
) {
    /** 차량 구독 공유 — 화면 수와 무관하게 SDK 리스너 1개. 구독자가 없으면(onStop) 즉시 해제 (FR-V03) */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0)
    private val vehicleOdometer = vehicle.odometerKm().shareIn(scope, sharing, replay = 1)

    val settings: Flow<AppSettings> = settingsStore.settings

    val items: Flow<List<ConsumableItem>> = dao.observeItems().map { l -> l.map { it.toDomain() } }

    private val records: Flow<List<ReplacementRecord>> = dao.observeRecords().map { l -> l.map { it.toDomain() } }

    /** Odometer: 차량값 → (없으면) 마지막 저장값 → 수동값. 내부 단위는 항상 km (FR-V06) */
    val odometer: Flow<OdometerState> = combine(
        vehicle.capabilities,
        vehicleOdometer.onStart { emit(null) },
        settingsStore.settings,
    ) { caps, vehicleKm, s ->
        val manualMode = caps.resolved && (!caps.odometer || caps.permissionDenied)
        when {
            manualMode -> OdometerState(s.manualOdometerKm ?: s.lastVehicleOdometerKm ?: 0.0, OdoSource.MANUAL)
            vehicleKm != null -> OdometerState(vehicleKm, OdoSource.VEHICLE)
            s.lastVehicleOdometerKm != null -> OdometerState(s.lastVehicleOdometerKm, OdoSource.LAST_SAVED)
            s.manualOdometerKm != null -> OdometerState(s.manualOdometerKm, OdoSource.MANUAL)
            else -> OdometerState(0.0, OdoSource.NONE)
        }
    }.distinctUntilChanged()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val avgKmPerDay: Flow<Double?> = todayProvider.today.flatMapLatest { today ->
        dao.observeSnapshots(today.minusDays(30).toEpochDay()).map { snaps ->
            LifeCalculator.averageKmPerDay(snaps.map { LocalDate.ofEpochDay(it.epochDay) to it.maxKm }, today)
        }
    }

    private data class VehicleSignals(
        val caps: VehicleCapabilities, val fluid: FluidAlerts, val live: VehicleLive, val api: Map<VehicleApi, ApiResult>,
    ) {
        val tires: Map<WheelPos, Boolean> get() = live.lowTires
    }

    private val signals: Flow<VehicleSignals> = combine(
        vehicle.capabilities,
        vehicle.fluidAlerts().onStart { emit(FluidAlerts()) },
        vehicle.liveData().onStart { emit(VehicleLive()) },
        vehicle.apiStatus,
    ) { c, f, l, a -> VehicleSignals(c, f, l, a) }

    private data class Stored(val items: List<ConsumableItem>, val records: List<ReplacementRecord>, val settings: AppSettings)

    private val stored: Flow<Stored> = combine(items, records, settingsStore.settings) { i, r, s -> Stored(i, r, s) }

    val dashboard: Flow<DashboardData> = combine(
        stored, odometer, signals, avgKmPerDay, todayProvider.today,
    ) { st, odo, sig, avg, today ->
        val s = st.settings
        val byItem = st.records.groupBy { it.itemId }
        val states = st.items
            .filter { it.enabled && it.appliesTo(s.powertrain) }
            .mapNotNull { item ->
                val recs = byItem[item.id].orEmpty() // 최신순 정렬됨 (DAO)
                val realCount = recs.count { it.source != RecordSource.ESTIMATED }
                val interval = item.activeInterval(s.drivingCondition, realCount)
                if (interval.isEmpty) return@mapNotNull null // 주기 없음 → 숨김
                val last = recs.firstOrNull()
                val result = LifeCalculator.calculate(
                    LifeInput(
                        odoNowKm = odo.km, today = today, interval = interval,
                        last = last?.let { LastReplacement(it.odometerKm, it.date) },
                        avgKmPerDay = avg, thresholds = s.thresholds,
                    ),
                )
                ConsumableState(
                    item = item, interval = interval,
                    usesFirstInterval = item.first != null && realCount == 0,
                    result = result,
                    sensorAlert = sensorAlertFor(item.sensorLink, sig),
                    lastRecord = last, records = recs,
                    cycle = CycleCalculator.of(result, interval, recs, odo.km),
                    kind = item.activeKind(s.drivingCondition),
                )
            }
            .sortedForDashboard({ it.displayStatus }, { it.result.remainingRatio })
        DashboardData(
            odometer = odo, items = states, settings = s, capabilities = sig.caps,
            fluid = sig.fluid, tires = sig.tires, collectingData = avg == null, today = today,
            live = sig.live, apiStatus = sig.api, avgKmPerDay = avg,
        )
    }.shareIn(scope, sharing, replay = 1)

    private fun sensorAlertFor(link: SensorLink?, sig: VehicleSignals): SensorAlert? = when (link) {
        SensorLink.ENGINE_OIL_LEVEL -> SensorAlert.EngineOilLow.takeIf { sig.caps.engineOilLevel && sig.fluid.engineOil.isLow }
        SensorLink.BRAKE_FLUID_LEVEL -> SensorAlert.BrakeFluidLow.takeIf { sig.caps.brakeFluidLevel && sig.fluid.brakeFluid.isLow }
        SensorLink.ATF_LEVEL -> SensorAlert.TransmissionOilLow.takeIf { sig.caps.transmissionOilLevel && sig.fluid.transmissionOil.isLow }
        SensorLink.WASHER -> SensorAlert.WasherFluidLow.takeIf { sig.caps.washerFluid && sig.fluid.washerLow }
        // 공기압 수치(바퀴별)가 있으면 그 바퀴를, 없고 TPMS 경고등만 켜져 있으면 위치 미상으로 알린다
        SensorLink.TIRE -> sig.tires.filterValues { it }.keys.let { low ->
            when {
                low.isNotEmpty() -> SensorAlert.TirePressureLow(low)
                sig.live.telltaleOn(Telltale.TPMS) -> SensorAlert.TirePressureLow(emptySet())
                else -> null
            }
        }
        SensorLink.EV_POWER -> SensorAlert.EvPowerLimited.takeIf {
            sig.live.powerDownWarning == true || sig.live.telltaleOn(Telltale.POWER_DOWN) || sig.live.telltaleOn(Telltale.EV_SYSTEM)
        }
        SensorLink.LOW_VOLT_BATTERY -> SensorAlert.LowVoltBattery.takeIf { sig.live.telltaleOn(Telltale.LOW_VOLT_BATTERY) }
        null -> null
    }

    // ---------- 주행 스냅샷 (FR-V05, FR-C01) ----------

    private var lastSavedKm: Double? = null
    private var lastSavedAt: Long = 0L

    /** foreground(STARTED) 동안만 수집 — MainActivity.repeatOnLifecycle */
    suspend fun trackOdometer() {
        vehicleOdometer.collect { km ->
            if (km == null) return@collect
            val now = System.currentTimeMillis()
            val today = LocalDate.now().toEpochDay()
            val existing = dao.snapshot(today)
            val firstOfDay = existing == null
            val prev = lastSavedKm
            // "1 km 이상 변화 또는 10분 경과 중 늦은 쪽" → 두 조건 모두 충족 시 저장. 그날 첫 값·세션 첫 값은 즉시 저장
            val due = prev == null || firstOfDay ||
                (km - prev >= 1.0 && now - lastSavedAt >= SNAPSHOT_MIN_INTERVAL_MS)
            if (due) {
                dao.upsertSnapshot(OdometerSnapshotEntity(today, maxOf(km, existing?.maxKm ?: 0.0)))
                settingsStore.saveLastVehicleOdometer(km)
                lastSavedKm = km
                lastSavedAt = now
            }
        }
    }

    // ---------- 쓰기 ----------

    suspend fun recordReplacement(
        itemId: String, odometerKm: Double, date: LocalDate, source: RecordSource, memo: String? = null,
        action: RecordAction = RecordAction.REPLACED,
    ): Long = dao.insertRecord(
        ReplacementRecordEntity(
            itemId = itemId, odometerKm = odometerKm, dateEpochDay = date.toEpochDay(),
            source = source, memo = memo?.takeIf { it.isNotBlank() }, createdAt = System.currentTimeMillis(), action = action,
        ),
    )

    suspend fun updateRecord(r: ReplacementRecord) = dao.updateRecord(
        ReplacementRecordEntity(
            id = r.id, itemId = r.itemId, odometerKm = r.odometerKm, dateEpochDay = r.date.toEpochDay(),
            source = r.source, memo = r.memo, createdAt = System.currentTimeMillis(), action = r.action,
        ),
    )

    suspend fun deleteRecord(id: Long) = dao.deleteRecord(id)

    suspend fun updateItem(item: ConsumableItem) = dao.updateItem(item.toEntity())

    suspend fun addCustomItem(name: String, interval: Interval): String? {
        val all = items.first()
        val customs = all.filter { it.isCustom }
        if (customs.size >= ConsumableItem.MAX_CUSTOM) return null
        val n = (1..ConsumableItem.MAX_CUSTOM).first { i -> customs.none { it.id == "${ConsumableItem.CUSTOM_PREFIX}$i" } }
        val item = ConsumableItem(
            id = "${ConsumableItem.CUSTOM_PREFIX}$n", customName = name.trim(), iconKey = "custom",
            normal = interval, severe = interval, first = null, enabled = true,
            sortOrder = (all.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            powertrains = Powertrain.entries.toSet(), sensorLink = null, zone = CarZone.OTHER,
            cycleKind = CycleKind.REPLACE, isUserDefault = false,
        )
        dao.insertItems(listOf(item.toEntity()))
        return item.id
    }

    suspend fun deleteCustomItem(id: String) {
        dao.deleteRecordsFor(id)
        dao.deleteItem(id)
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    /** 온보딩 완료: 프리셋 적재 + 초기 교체 기록 */
    suspend fun completeOnboarding(
        powertrain: Powertrain,
        condition: Condition,
        manualOdometerKm: Double?,
        initialRecords: List<InitialRecord>,
        seedDemoSnapshots: Boolean,
    ) {
        val preset = presetLoader.load()
        if (dao.itemCount() == 0) dao.insertItems(preset.items.map { it.toEntity() })
        initialRecords.forEach { recordReplacement(it.itemId, it.odometerKm, it.date, it.source, action = it.action) }
        if (seedDemoSnapshots) {
            val end = initialRecords.maxOfOrNull { it.odometerKm } ?: 0.0
            val today = LocalDate.now()
            (0..30).forEach { d ->
                dao.upsertSnapshot(OdometerSnapshotEntity(today.minusDays(d.toLong()).toEpochDay(), (end - d * DEMO_KM_PER_DAY).coerceAtLeast(0.0)))
            }
        }
        settingsStore.update {
            it.copy(
                powertrain = powertrain, drivingCondition = condition,
                manualOdometerKm = manualOdometerKm ?: it.manualOdometerKm, onboardingDone = true,
                profileId = preset.profileId, presetRev = preset.rev,
            )
        }
    }

    /**
     * 프로필 동기화 (명세 v2.0 0장 "대체"): 온보딩을 이미 마친 설치에서 프리셋 JSON의 profileId가 바뀌었으면
     * 프리셋 항목을 새 품목으로 교체한다. 사용자 항목(CUSTOM_*)·교체 기록·같은 id 항목의 표시 여부는 유지.
     */
    suspend fun syncProfile() {
        val s = settingsStore.current()
        if (!s.onboardingDone) return
        val preset = presetLoader.load()
        if (s.profileId == preset.profileId && dao.itemCount() > 0) {
            // 프리셋 개정(rev): 품목·주기·기록은 그대로 두고 차량 API 연결(sensorLink)만 새 프리셋 값으로 맞춘다
            if (s.presetRev != preset.rev) {
                val links = preset.items.associate { it.id to it.sensorLink }
                dao.items().filter { it.id in links && it.sensorLink != links[it.id] }
                    .forEach { dao.updateItem(it.copy(sensorLink = links[it.id])) }
                settingsStore.update { it.copy(presetRev = preset.rev) }
            }
            return
        }
        val existing = dao.items().associateBy { it.id }
        dao.deletePresetItems()
        dao.insertItems(preset.items.map { p -> existing[p.id]?.let { p.copy(enabled = it.enabled) } ?: p }.map { it.toEntity() })
        settingsStore.update { it.copy(profileId = preset.profileId, presetRev = preset.rev) }
    }

    suspend fun presetItems(): List<ConsumableItem> = presetLoader.load().items

    suspend fun resetAll() {
        dao.clearRecords(); dao.clearSnapshots(); dao.clearItems()
        settingsStore.clear()
        lastSavedKm = null
    }

    data class InitialRecord(
        val itemId: String, val odometerKm: Double, val date: LocalDate, val source: RecordSource,
        val action: RecordAction = RecordAction.REPLACED,
    )

    companion object {
        const val SNAPSHOT_MIN_INTERVAL_MS = 10 * 60 * 1000L
        const val DEMO_KM_PER_DAY = 42.0
    }
}
