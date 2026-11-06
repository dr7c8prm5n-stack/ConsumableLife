package com.example.consumablelife.core.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.WheelValue
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DrivingTimeListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import ai.pleos.playground.vehicle.listener.PowerDownWarningIndicatorListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.TirePressureListener
import android.content.Context
import android.util.Log
import com.example.consumablelife.core.domain.WheelPos
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Pleos Vehicle SDK 2.3.2 연동. 시그니처는 SDK sources.jar로 확인함 (모두 onComplete / onFailed 콜백형):
 *  Odometer  getCurrentDistanceDriven → Float? km (누적 주행거리, 수명 계산의 기준)
 *            getTripCumulativeDrivingDistance → Float? m · getTripCumulativeDrivingTime → Int? 분 (누적 트립 → 평균 속도)
 *  Safety    get(EngineOil|BrakeFluid|TransmissionOil)LevelState → OilLevelState · hasLowWasherFluidWarning → Boolean?
 *            hasPowerDownWarningIndicator → Boolean? (출력 제한) · getTelltalesInfo → Telltales (PRIVATE_CAR_SAFETY)
 *  EvBattery getEvBatteryStateOfCharge → Int? % (CAR_ENERGY)
 *  Tire      getLowTirePressure(WheelArea) → Float? kPa (저압 기준) · getTirePressure(WheelArea) → Float? kPa (PRIVATE_CAR_SAFETY)
 *
 * PRIVATE_* 권한(signature|privileged)은 일반 앱에 부여되지 않는다 → 호출은 시도하되 실패하면 "권한 없음"으로 기록하고
 * 해당 값 없이 동작한다 (공기압 수치·경고등). 각 API의 결과는 [apiStatus]로 노출해 설정 화면에서 확인할 수 있다.
 *
 * FR-V01: 프로세스당 Vehicle 1개(@Singleton), 첫 구독 시점에 지연 initialize().
 * FR-V03: 리스너는 Flow 수집(onStart~onStop) 동안만 등록. Vehicle은 프로세스 수명 동안 유지(release는 호출하지 않음 —
 *         진행 중 콜백이 있을 때 release 금지 규칙을 단순하게 보장).
 */
@Singleton
class PleosVehicleDataSource @Inject constructor(
    @ApplicationContext private val ctx: Context,
) : VehicleDataSource {

    private val initLock = Mutex()
    private var vehicle: Vehicle? = null

    private val _capabilities = MutableStateFlow(VehicleCapabilities())
    override val capabilities: StateFlow<VehicleCapabilities> = _capabilities.asStateFlow()

    private val _apiStatus = MutableStateFlow<Map<VehicleApi, ApiResult>>(emptyMap())
    override val apiStatus: StateFlow<Map<VehicleApi, ApiResult>> = _apiStatus.asStateFlow()

    private suspend fun vehicle(): Vehicle = initLock.withLock {
        vehicle ?: Vehicle(ctx).also {
            it.initialize()
            vehicle = it
        }
    }

    // ---------- Capabilities (FR-V02) ----------

    private val capsLock = Mutex()

    /** capability가 읽기 가능으로 확인된 API */
    @Volatile private var readable: Set<VehicleApi> = emptySet()
    @Volatile private var wheels: List<Pair<WheelPos, WheelArea>> = emptyList()

    private fun can(api: VehicleApi) = api in readable

    private suspend fun ensureCapabilities() = capsLock.withLock {
        if (_capabilities.value.resolved) return@withLock
        val v = vehicle()
        val odometer = v.getOdometer()
        val safety = v.getSafety()
        val tire = v.getTire()
        val ev = v.getEvBattery()

        // 12개 capability를 동시에 확인 (하나가 재시도 중이어도 나머지를 막지 않음)
        val results: Map<VehicleApi, Cap<*>> = coroutineScope {
            mapOf(
                VehicleApi.ODOMETER to async { cap { ok, f -> odometer.checkCurrentDistanceDrivenCapabilities(ok, f) } },
                VehicleApi.TRIP_DISTANCE to async { cap { ok, f -> odometer.checkTripCumulativeDrivingDistanceCapabilities(ok, f) } },
                VehicleApi.TRIP_TIME to async { cap { ok, f -> odometer.checkTripCumulativeDrivingTimeCapabilities(ok, f) } },
                VehicleApi.ENGINE_OIL to async { cap2<List<OilLevelState>> { ok, f -> safety.checkEngineOilLevelStateCapabilities(ok, f) } },
                VehicleApi.BRAKE_FLUID to async { cap2<List<OilLevelState>> { ok, f -> safety.checkBrakeFluidLevelStateCapabilities(ok, f) } },
                VehicleApi.TRANSMISSION_OIL to async { cap2<List<OilLevelState>> { ok, f -> safety.checkTransmissionOilLevelStateCapabilities(ok, f) } },
                VehicleApi.WASHER to async { cap { ok, f -> safety.checkLowWasherFluidWarningStateCapabilities(ok, f) } },
                VehicleApi.POWER_DOWN to async { cap { ok, f -> safety.checkPowerDownWarningIndicatorCapabilities(ok, f) } },
                VehicleApi.EV_SOC to async { cap { ok, f -> ev.checkEvBatteryStateOfChargeCapabilities(ok, f) } },
                VehicleApi.TIRE_THRESHOLD to async { cap2<List<WheelArea>> { ok, f -> tire.checkLowTirePressureCapabilities(ok, f) } },
                VehicleApi.TIRE_PRESSURE to async { cap2<List<WheelArea>> { ok, f -> tire.checkTirePressureCapabilities(ok, f) } },
                VehicleApi.TELLTALES to async { cap { ok, f -> safety.checkTelltalesCapabilities(ok, f) } },
            ).mapValues { it.value.await() }
        }

        @Suppress("UNCHECKED_CAST")
        val areas = listOf(VehicleApi.TIRE_PRESSURE, VehicleApi.TIRE_THRESHOLD)
            .firstNotNullOfOrNull { (results.getValue(it).extra as? List<WheelArea>)?.takeIf { l -> l.isNotEmpty() } }
            .orEmpty()
        wheels = areas.flatMap { area -> area.values.mapNotNull { w -> w.toPos()?.let { it to WheelArea(w) } } }.distinctBy { it.first }
        readable = results.filterValues { it.readable }.keys

        _apiStatus.value = results.mapValues { (_, c) ->
            when {
                c.permissionDenied -> ApiResult(ApiState.PERMISSION_DENIED, c.error)
                c.readable -> ApiResult(ApiState.NO_VALUE)
                c.error != null -> ApiResult(if (c.error in NON_RETRYABLE) ApiState.UNSUPPORTED else ApiState.FAILED, c.error)
                else -> ApiResult(ApiState.UNSUPPORTED)
            }
        }
        results.filterValues { it.permissionDenied }.keys.forEach { Log.w(TAG, "permission denied: ${it.api} (${it.permission})") }
        // 연동 점검용: capability 결과를 한 줄씩 남긴다 (adb logcat -s Cartalog)
        _apiStatus.value.forEach { (api, r) -> Log.i(TAG, "capability ${api.name} [${api.permission}] = ${r.state}${r.detail?.let { " ($it)" } ?: ""}") }

        _capabilities.value = VehicleCapabilities(
            resolved = true,
            odometer = can(VehicleApi.ODOMETER),
            engineOilLevel = can(VehicleApi.ENGINE_OIL),
            brakeFluidLevel = can(VehicleApi.BRAKE_FLUID),
            transmissionOilLevel = can(VehicleApi.TRANSMISSION_OIL),
            washerFluid = can(VehicleApi.WASHER),
            tirePressure = can(VehicleApi.TIRE_PRESSURE) && wheels.isNotEmpty(),
            // 수동 주행거리 모드 전환은 Odometer 권한 거부일 때만 — 다른 도메인 거부는 해당 값만 비움 (FR-V02/V04)
            permissionDenied = results.getValue(VehicleApi.ODOMETER).permissionDenied,
        )
    }

    private class Cap<E>(val readable: Boolean, val extra: E?, val permissionDenied: Boolean, val error: String?)

    private suspend fun cap(block: ((CapabilityConfig) -> Unit, (Exception) -> Unit) -> Unit): Cap<Unit> {
        val r = callWithRetry<CapabilityConfig>(block = block)
        return Cap(r.value?.readable == true, Unit, r.permissionDenied, r.error)
    }

    private suspend fun <E> cap2(block: ((CapabilityConfig, E) -> Unit, (Exception) -> Unit) -> Unit): Cap<E> {
        val r = callWithRetry<Pair<CapabilityConfig, E>> { ok, fail -> block({ c, e -> ok(c to e) }, fail) }
        return Cap(r.value?.first?.readable == true, r.value?.second, r.permissionDenied, r.error)
    }

    private val CapabilityConfig.readable: Boolean
        get() = accessType == AccessType.READ || accessType == AccessType.READ_WRITE

    /**
     * 값 1개를 읽고(get) 변경을 구독(register)한다.
     * capability가 "미지원"이라고 답해도 get을 한 번은 시도한다 — 차종·에뮬레이터에 따라 capability 조회만 구현되지 않은 속성이 있어서다.
     * 이때는 재시도하지 않고, 값이 오면 연동으로 승격해 리스너를 등록한다. 권한 거부(PRIVATE_*)는 호출하지 않는다.
     */
    private fun <T : Any> ProducerScope<*>.attach(
        api: VehicleApi,
        cleanup: MutableList<() -> Unit>,
        get: ((T?) -> Unit, (Exception) -> Unit) -> Unit,
        describe: (T) -> String,
        onValue: (T) -> Unit,
        register: () -> (() -> Unit),
    ) {
        if (_apiStatus.value[api]?.state == ApiState.PERMISSION_DENIED) return
        val supported = can(api)
        if (supported) cleanup += register()
        launch {
            val r = callWithRetry<T?>(maxRetry = if (supported) MAX_RETRY else 0, block = get)
            val v = r.value
            Log.i(TAG, "get ${api.name} -> " + (v?.let(describe) ?: "FAILED(${r.error ?: "no value"})") + if (supported) "" else " [probe]")
            when {
                v != null -> {
                    connected(api, describe(v))
                    onValue(v)
                    if (!supported) { readable = readable + api; runCatching { cleanup += register() } }
                }
                r.permissionDenied -> _apiStatus.update { it + (api to ApiResult(ApiState.PERMISSION_DENIED, r.error)) }
                supported -> _apiStatus.update { it + (api to ApiResult(if (r.error != null) ApiState.FAILED else ApiState.NO_VALUE, r.error)) }
                else -> Unit // capability·get 모두 미지원 → UNSUPPORTED 유지
            }
        }
    }

    private fun connected(api: VehicleApi, detail: String) = _apiStatus.update { it + (api to ApiResult(ApiState.CONNECTED, detail)) }

    // ---------- Odometer ----------

    override fun odometerKm(): Flow<Double?> = callbackFlow {
        ensureCapabilities()
        if (!can(VehicleApi.ODOMETER)) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val odometer = vehicle().getOdometer()
        val cleanup = CopyOnWriteArrayList<() -> Unit>()
        attach<Float>(
            VehicleApi.ODOMETER, cleanup,
            get = { ok, f -> odometer.getCurrentDistanceDriven(ok, f) },
            describe = { "${it.roundToInt()} km" },
            onValue = { trySend(it.toDouble()) },
            register = {
                val l = object : DistanceDrivenListener {
                    override fun onDistanceDrivenUpdated(value: Float?) {
                        if (value != null) { trySend(value.toDouble()); connected(VehicleApi.ODOMETER, "${value.roundToInt()} km") }
                    }
                    override fun onFailed(e: Exception) = log("distanceDriven", e)
                }
                odometer.registerDistanceDriven(l); { odometer.unregisterDistanceDriven(l) }
            },
        )
        awaitClose { cleanup.forEach { runCatching(it) } }
    }.distinctUntilChanged { a, b -> a?.toInt() == b?.toInt() } // FR-V05 정수 km 버킷
    // 단위: SDK 문서상 getCurrentDistanceDriven은 km 고정 (mile 차종도 km로 정규화되는지는 실차 VERIFY, FR-V06)

    // ---------- Safety: 오일 레벨·워셔액 ----------

    override fun fluidAlerts(): Flow<FluidAlerts> = callbackFlow {
        ensureCapabilities()
        val safety = vehicle().getSafety()
        val state = MutableStateFlow(FluidAlerts())
        launch { state.collect { trySend(it) } }
        val cleanup = CopyOnWriteArrayList<() -> Unit>()

        fun level(
            api: VehicleApi,
            get: ((OilLevelState?) -> Unit, (Exception) -> Unit) -> Unit,
            reg: (OilLevelStateListener) -> Unit, unreg: (OilLevelStateListener) -> Unit,
            set: (FluidAlerts, LevelState) -> FluidAlerts,
        ) = attach<OilLevelState>(
            api, cleanup, get = get, describe = { it.name },
            onValue = { v -> state.update { set(it, v.toLevel()) } },
            register = {
                val l = object : OilLevelStateListener {
                    override fun onOilLevelUpdated(state_: OilLevelState?) {
                        if (state_ != null) { state.update { set(it, state_.toLevel()) }; connected(api, state_.name) }
                    }
                    override fun onFailed(e: Exception) = log("oilLevel", e)
                }
                reg(l); { unreg(l) }
            },
        )
        level(VehicleApi.ENGINE_OIL, { ok, f -> safety.getEngineOilLevelState(ok, f) }, safety::registerEngineOilLevelState, safety::unregisterEngineOilLevelState) { a, v -> a.copy(engineOil = v) }
        level(VehicleApi.BRAKE_FLUID, { ok, f -> safety.getBrakeFluidLevelState(ok, f) }, safety::registerBrakeFluidLevelState, safety::unregisterBrakeFluidLevelState) { a, v -> a.copy(brakeFluid = v) }
        level(VehicleApi.TRANSMISSION_OIL, { ok, f -> safety.getTransmissionOilLevelState(ok, f) }, safety::registerTransmissionOilLevelState, safety::unregisterTransmissionOilLevelState) { a, v -> a.copy(transmissionOil = v) }
        attach<Boolean>(
            VehicleApi.WASHER, cleanup,
            get = { ok, f -> safety.hasLowWasherFluidWarning(ok, f) },
            describe = { if (it) "LOW" else "NORMAL" },
            onValue = { low -> state.update { it.copy(washerLow = low) } },
            register = {
                val l = object : LowWasherFluidWaringListener {
                    override fun onLowWasherFluidWaringUpdated(low: Boolean?) {
                        if (low != null) { state.update { it.copy(washerLow = low) }; connected(VehicleApi.WASHER, if (low) "LOW" else "NORMAL") }
                    }
                    override fun onFailed(e: Exception) = log("washer", e)
                }
                safety.registerLowWasherFluidWaringState(l); { safety.unregisterLowWasherFluidWaringState(l) }
            },
        )
        awaitClose { cleanup.forEach { runCatching(it) } }
    }.distinctUntilChanged()

    private fun OilLevelState.toLevel(): LevelState = when (this) {
        OilLevelState.NORMAL -> LevelState.NORMAL
        OilLevelState.LOW -> LevelState.LOW
        OilLevelState.CRITICALLY_LOW -> LevelState.CRITICALLY_LOW
        OilLevelState.HIGH -> LevelState.HIGH
        OilLevelState.ERROR -> LevelState.ERROR
    }

    // ---------- 품목 매칭 실측값: 누적 트립 · 충전량 · 출력 제한 · 공기압 · 경고등 ----------

    override fun liveData(): Flow<VehicleLive> = callbackFlow {
        ensureCapabilities()
        val v = vehicle()
        val odometer = v.getOdometer()
        val safety = v.getSafety()
        val ev = v.getEvBattery()
        val tire = v.getTire()
        val wheelList = wheels
        val state = MutableStateFlow(VehicleLive(tireWheels = wheelList.map { it.first }.toSet()))
        launch { state.collect { trySend(it) } }
        val cleanup = CopyOnWriteArrayList<() -> Unit>()

        // 누적 트립 거리 (m → km) — Odometer.getTripCumulativeDrivingDistance
        attach<Float>(
            VehicleApi.TRIP_DISTANCE, cleanup,
            get = { ok, f -> odometer.getTripCumulativeDrivingDistance(ok, f) },
            describe = { "${it.roundToInt()} m" },
            onValue = { m -> state.update { it.copy(tripCumulativeKm = m / 1000.0) } },
            register = {
                val l = object : DistanceDrivenListener {
                    override fun onDistanceDrivenUpdated(value: Float?) {
                        if (value != null) { state.update { it.copy(tripCumulativeKm = value / 1000.0) }; connected(VehicleApi.TRIP_DISTANCE, "${value.roundToInt()} m") }
                    }
                    override fun onFailed(e: Exception) = log("tripDistance", e)
                }
                odometer.registerTripCumulativeDrivingDistance(l); { odometer.unregisterTripCumulativeDrivingDistance(l) }
            },
        )
        // 누적 운전 시간 (분)
        attach<Int>(
            VehicleApi.TRIP_TIME, cleanup,
            get = { ok, f -> odometer.getTripCumulativeDrivingTime(ok, f) },
            describe = { "$it min" },
            onValue = { m -> state.update { it.copy(tripCumulativeMinutes = m) } },
            register = {
                val l = object : DrivingTimeListener {
                    override fun onDrivingTimeUpdated(value: Int?) {
                        if (value != null) { state.update { it.copy(tripCumulativeMinutes = value) }; connected(VehicleApi.TRIP_TIME, "$value min") }
                    }
                    override fun onFailed(e: Exception) = log("tripTime", e)
                }
                odometer.registerTripCumulativeDrivingTime(l); { odometer.unregisterTripCumulativeDrivingTime(l) }
            },
        )
        // 고전압 배터리 충전량 (%)
        attach<Int>(
            VehicleApi.EV_SOC, cleanup,
            get = { ok, f -> ev.getEvBatteryStateOfCharge(ok, f) },
            describe = { "$it %" },
            onValue = { p -> state.update { it.copy(evSocPercent = p) } },
            register = {
                val l = object : EvBatteryStateOfChargeListener {
                    override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
                        if (value != null) { state.update { it.copy(evSocPercent = value) }; connected(VehicleApi.EV_SOC, "$value %") }
                    }
                    override fun onFailed(e: Exception) = log("evSoc", e)
                }
                ev.registerEvBatteryStateOfCharge(l); { ev.unregisterEvBatteryStateOfCharge(l) }
            },
        )
        // 출력 제한(Power Down) 경고
        attach<Boolean>(
            VehicleApi.POWER_DOWN, cleanup,
            get = { ok, f -> safety.hasPowerDownWarningIndicator(ok, f) },
            describe = { if (it) "ON" else "OFF" },
            onValue = { on -> state.update { it.copy(powerDownWarning = on) } },
            register = {
                val l = object : PowerDownWarningIndicatorListener {
                    override fun onPowerDownWarningIndicatorUpdated(state_: Boolean?) {
                        if (state_ != null) { state.update { it.copy(powerDownWarning = state_) }; connected(VehicleApi.POWER_DOWN, if (state_) "ON" else "OFF") }
                    }
                    override fun onFailed(e: Exception) = log("powerDown", e)
                }
                safety.registerPowerDownWarningIndicator(l); { safety.unregisterPowerDownWarningIndicator(l) }
            },
        )
        // 타이어: 저압 기준값(공개) + 현재 공기압(PRIVATE — 권한이 없으면 호출하지 않음)
        wheelList.forEach { (pos, area) ->
            attach<Float>(
                VehicleApi.TIRE_THRESHOLD, cleanup,
                get = { ok, f -> tire.getLowTirePressure(area, ok, f) },
                describe = { "${it.roundToInt()} kPa" },
                onValue = { p -> state.update { it.copy(tireLowThresholdKpa = it.tireLowThresholdKpa + (pos to p)) } },
                register = { {} }, // 기준값은 변하지 않음 — 리스너 없음
            )
            attach<Float>(
                VehicleApi.TIRE_PRESSURE, cleanup,
                get = { ok, f -> tire.getTirePressure(area, ok, f) },
                describe = { "${it.roundToInt()} kPa" },
                onValue = { p -> state.update { it.copy(tirePressureKpa = it.tirePressureKpa + (pos to p)) } },
                register = {
                    val l = object : TirePressureListener {
                        override fun onTirePressureUpdated(pressure: Float?) {
                            if (pressure != null) state.update { it.copy(tirePressureKpa = it.tirePressureKpa + (pos to pressure)) }
                        }
                        override fun onFailed(e: Exception) = log("tirePressure", e)
                    }
                    tire.registerTirePressure(area, l); { tire.unregisterTirePressure(area, l) }
                },
            )
        }
        // 경고등 (PRIVATE): TPMS·12V 배터리·EV 시스템
        fun onTelltales(t: Telltales) {
            val on = t.telltales.filter { it.state != TelltaleState.OFF }.mapNotNull { it.type.toDomain() }.toSet()
            state.update { it.copy(telltales = on) }
            connected(VehicleApi.TELLTALES, if (on.isEmpty()) "OFF" else on.joinToString { it.name })
        }
        attach<Telltales>(
            VehicleApi.TELLTALES, cleanup,
            get = { ok, f -> safety.getTelltalesInfo({ ok(it) }, f) },
            describe = { "${it.telltales.size} items" },
            onValue = ::onTelltales,
            register = {
                val l = object : TelltaleInfoListener {
                    override fun onTelltaleInfoUpdated(telltales: Telltales?) {
                        if (telltales != null) onTelltales(telltales)
                    }
                    override fun onFailed(e: Exception) = log("telltales", e)
                }
                safety.registerTelltales(l); { safety.unregisterTelltales(l) }
            },
        )
        awaitClose { cleanup.forEach { runCatching(it) } }
    }.distinctUntilChanged()

    private fun TelltaleType.toDomain(): Telltale? = when (this) {
        TelltaleType.TPMS -> Telltale.TPMS
        TelltaleType.LOW_VOLT_BATTERY -> Telltale.LOW_VOLT_BATTERY
        TelltaleType.EV_SYSTEM -> Telltale.EV_SYSTEM
        TelltaleType.LOW_EV_BATTERY -> Telltale.LOW_EV_BATTERY
        TelltaleType.POWER_DOWN -> Telltale.POWER_DOWN
        TelltaleType.ABS -> Telltale.ABS
        TelltaleType.MASTER_WARNING -> Telltale.MASTER_WARNING
        else -> null
    }

    private fun WheelValue.toPos(): WheelPos? = when (this) {
        WheelValue.LEFT_FRONT -> WheelPos.FRONT_LEFT
        WheelValue.RIGHT_FRONT -> WheelPos.FRONT_RIGHT
        WheelValue.LEFT_REAR -> WheelPos.REAR_LEFT
        WheelValue.RIGHT_REAR -> WheelPos.REAR_RIGHT
        else -> null
    }

    // ---------- CarInfo ----------

    /** SDK CarInfo는 VIN만 제공(차량명 API 없음) → 차량명은 표시 생략 (S0 "실패 시 생략") */
    override suspend fun vehicleInfo(): VehicleInfo? = null

    // ---------- 공통: 콜백 → suspend + FR-V04 재시도 ----------

    private class CallResult<T>(val value: T?, val permissionDenied: Boolean, val error: String? = null)

    private suspend fun <T> callWithRetry(
        maxRetry: Int = MAX_RETRY,
        block: ((T) -> Unit, (Exception) -> Unit) -> Unit,
    ): CallResult<T> {
        var attempt = 0
        while (true) {
            val outcome = suspendCancellableCoroutine<Result<T>> { cont ->
                try {
                    block({ v -> if (cont.isActive) cont.resume(Result.success(v)) },
                        { e -> if (cont.isActive) cont.resume(Result.failure(e)) })
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(Result.failure(e))
                }
            }
            outcome.onSuccess { return CallResult(it, false) }
            val e = outcome.exceptionOrNull()
            // ErrorCode 이름이 없으면 예외 메시지(짧게) 또는 클래스 이름을 남긴다
            val code = errorCodeOf(e) ?: e?.message?.takeIf { it.isNotBlank() }?.take(48) ?: e?.javaClass?.simpleName
            log("call(code=$code, attempt=$attempt)", e)
            when {
                code == "ERROR_SECURITY_PERMISSION" || e is SecurityException ||
                    e?.message?.contains("permission", ignoreCase = true) == true -> {
                    return CallResult(null, true, code) // 재시도하지 않음 — 권한 미승인 상태로 기록
                }
                code in NON_RETRYABLE -> return CallResult(null, false, code)
                attempt >= maxRetry -> return CallResult(null, false, code)
            }
            delay(BACKOFF_BASE_MS shl attempt) // 지수 백오프 0.5s, 1s, 2s
            attempt++
        }
    }

    /** SDK VehicleException의 message에 ErrorCode 이름이 담긴다 */
    private fun errorCodeOf(e: Throwable?): String? {
        val msg = e?.message ?: return null
        return ERROR_CODES.firstOrNull { msg.contains(it) }
    }

    private fun log(where: String, e: Throwable?) {
        Log.w(TAG, "Pleos $where failed: ${e?.message}")
    }

    companion object {
        private const val TAG = "Cartalog"
        private const val MAX_RETRY = 3
        private const val BACKOFF_BASE_MS = 500L
        private val ERROR_CODES = listOf(
            "ERROR_INTERNAL_SERVICE", "ERROR_SERVICE_CONNECTION_FAILURE", "ERROR_DRIVING_RESTRICTED",
            "ERROR_NOT_SUPPORTED_PROPERTY", "ERROR_NOT_SUPPORTED_VALUE_TYPE", "ERROR_NOT_SUPPORTED_AREA",
            "ERROR_EMPTY_RESULT", "ERROR_SECURITY_PERMISSION", "ERROR_UNDEFINED",
        )
        private val NON_RETRYABLE = setOf(
            "ERROR_NOT_SUPPORTED_PROPERTY", "ERROR_NOT_SUPPORTED_VALUE_TYPE", "ERROR_NOT_SUPPORTED_AREA",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleModule {
    @Binds
    abstract fun bindVehicleDataSource(impl: PleosVehicleDataSource): VehicleDataSource
}
