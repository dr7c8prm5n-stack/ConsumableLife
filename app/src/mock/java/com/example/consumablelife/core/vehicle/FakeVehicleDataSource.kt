package com.example.consumablelife.core.vehicle

import com.example.consumablelife.core.domain.WheelPos
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * 개발·테스트용 차량 (T4). 시나리오: 주행 증가, 오일·브레이크액 레벨 저하, 타이어 저압, 고전압 출력 제한, 12V 경고등,
 * Odometer 미지원, 권한 미승인.
 * DRIVING 시나리오만 앱이 foreground로 구독 중일 때 값이 증가 (백그라운드 폴링 없음, NFR-04).
 */
@Singleton
class FakeVehicleDataSource @Inject constructor() : VehicleDataSource, VehicleDebugControls {

    private val odo = MutableStateFlow(INITIAL_ODO_KM)
    private val _scenario = MutableStateFlow(FakeScenario.NORMAL)
    override val scenario: StateFlow<FakeScenario> = _scenario.asStateFlow()

    private val _capabilities = MutableStateFlow(capabilitiesFor(FakeScenario.NORMAL))
    override val capabilities: StateFlow<VehicleCapabilities> = _capabilities.asStateFlow()

    private val _apiStatus = MutableStateFlow(statusFor(FakeScenario.NORMAL))
    override val apiStatus: StateFlow<Map<VehicleApi, ApiResult>> = _apiStatus.asStateFlow()

    override val debugControls: VehicleDebugControls get() = this

    override fun setScenario(scenario: FakeScenario) {
        _scenario.value = scenario
        _capabilities.value = capabilitiesFor(scenario)
        _apiStatus.value = statusFor(scenario)
    }

    override fun addKm(km: Double) = odo.update { it + km }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun odometerKm(): Flow<Double?> = _scenario.flatMapLatest { s ->
        when (s) {
            FakeScenario.ODOMETER_UNSUPPORTED, FakeScenario.PERMISSION_DENIED -> flow { emit(null) }
            FakeScenario.DRIVING -> flow {
                while (true) {
                    emit(odo.value)
                    delay(DRIVE_TICK_MS)
                    odo.update { it + DRIVE_STEP_KM }
                }
            }
            else -> odo.map<Double, Double?> { it }
        }
    }.distinctUntilChanged { a, b -> a?.toInt() == b?.toInt() } // FR-V05 정수 km 버킷

    override fun fluidAlerts(): Flow<FluidAlerts> = _scenario.map { s ->
        when (s) {
            FakeScenario.PERMISSION_DENIED -> FluidAlerts()
            FakeScenario.OIL_LOW -> FluidAlerts(
                engineOil = LevelState.LOW, brakeFluid = LevelState.LOW,
                transmissionOil = LevelState.NORMAL, washerLow = true,
            )
            else -> FluidAlerts(
                engineOil = LevelState.NORMAL, brakeFluid = LevelState.NORMAL,
                transmissionOil = LevelState.NORMAL, washerLow = false,
            )
        }
    }.distinctUntilChanged()

    /** 실차의 누적 트립·충전량·공기압·경고등을 흉내 낸 값. 누적 트립은 주행거리와 함께 늘어난다 */
    override fun liveData(): Flow<VehicleLive> = combine(_scenario, odo) { s, km ->
        if (s == FakeScenario.PERMISSION_DENIED) return@combine VehicleLive()
        val driven = km - INITIAL_ODO_KM
        VehicleLive(
            tripCumulativeKm = TRIP_BASE_KM + driven,
            tripCumulativeMinutes = (TRIP_BASE_MIN + driven / TRIP_AVG_KMH * 60).roundToInt(),
            evSocPercent = (78 - driven / 5).roundToInt().coerceIn(5, 100),
            tireWheels = WheelPos.entries.toSet(),
            tirePressureKpa = WheelPos.entries.associateWith {
                if (it == WheelPos.FRONT_LEFT && s == FakeScenario.TIRE_LOW) TIRE_LOW_KPA else TIRE_NORMAL_KPA
            },
            tireLowThresholdKpa = WheelPos.entries.associateWith { TIRE_THRESHOLD_KPA },
            powerDownWarning = s == FakeScenario.EV_POWER_DOWN,
            telltales = when (s) {
                FakeScenario.TIRE_LOW -> setOf(Telltale.TPMS)
                FakeScenario.EV_POWER_DOWN -> setOf(Telltale.POWER_DOWN, Telltale.MASTER_WARNING)
                FakeScenario.LOW_12V -> setOf(Telltale.LOW_VOLT_BATTERY)
                else -> emptySet()
            },
        )
    }.distinctUntilChanged()

    override suspend fun vehicleInfo(): VehicleInfo = VehicleInfo(displayName = "Pleos 데모 차량 (mock)")

    private fun capabilitiesFor(s: FakeScenario) = when (s) {
        FakeScenario.PERMISSION_DENIED -> VehicleCapabilities(resolved = true, permissionDenied = true)
        else -> VehicleCapabilities(
            resolved = true,
            odometer = s != FakeScenario.ODOMETER_UNSUPPORTED,
            engineOilLevel = true, brakeFluidLevel = true, transmissionOilLevel = true,
            washerFluid = true, tirePressure = true,
        )
    }

    private fun statusFor(s: FakeScenario): Map<VehicleApi, ApiResult> = VehicleApi.entries.associateWith { api ->
        when {
            s == FakeScenario.PERMISSION_DENIED -> ApiResult(ApiState.PERMISSION_DENIED, "mock")
            s == FakeScenario.ODOMETER_UNSUPPORTED && api == VehicleApi.ODOMETER -> ApiResult(ApiState.UNSUPPORTED, "mock")
            else -> ApiResult(ApiState.CONNECTED, "mock")
        }
    }

    companion object {
        const val INITIAL_ODO_KM = 48_213.0
        const val DRIVE_STEP_KM = 50.0
        const val DRIVE_TICK_MS = 2_000L
        const val TRIP_BASE_KM = 12_480.0
        const val TRIP_BASE_MIN = 19_200
        const val TRIP_AVG_KMH = 39.0
        const val TIRE_NORMAL_KPA = 248f
        const val TIRE_LOW_KPA = 172f
        const val TIRE_THRESHOLD_KPA = 200f
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleModule {
    @Binds
    abstract fun bindVehicleDataSource(impl: FakeVehicleDataSource): VehicleDataSource
}
