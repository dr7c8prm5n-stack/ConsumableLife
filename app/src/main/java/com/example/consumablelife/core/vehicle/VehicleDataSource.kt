package com.example.consumablelife.core.vehicle

import com.example.consumablelife.core.domain.WheelPos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * UI/도메인이 의존하는 유일한 차량 인터페이스 (0장·4장).
 * Pleos SDK 클래스는 pleos flavor의 PleosVehicleDataSource에서만 import한다.
 */
interface VehicleDataSource {
    /** FR-V02 각 기능 지원 여부 — check…Capabilities 결과 */
    val capabilities: StateFlow<VehicleCapabilities>

    /** API별 연동 결과 (설정 > 차량 연동 상태). 키가 없으면 아직 확인 전 */
    val apiStatus: StateFlow<Map<VehicleApi, ApiResult>>

    /** 누적 주행거리(km 정규화). null = 미지원/권한 없음 → 수동 모드 */
    fun odometerKm(): Flow<Double?>

    /** 엔진오일/브레이크액/미션오일 레벨, 워셔액 부족 */
    fun fluidAlerts(): Flow<FluidAlerts>

    /** 품목과 매칭되는 차량 실측값·경고등 (읽기 전용). 수집 중에만 SDK 리스너 등록 (FR-V03) */
    fun liveData(): Flow<VehicleLive>

    /** 위치별 저압 여부 — liveData의 공기압 < 기준값에서 파생 */
    fun tireAlerts(): Flow<Map<WheelPos, Boolean>> = liveData().map { it.lowTires }.distinctUntilChanged()

    suspend fun vehicleInfo(): VehicleInfo?

    /** mock flavor 전용 시나리오 제어 (설정 > 개발자 옵션). pleos flavor는 null */
    val debugControls: VehicleDebugControls? get() = null
}

data class VehicleCapabilities(
    val resolved: Boolean = false,
    val odometer: Boolean = false,
    val engineOilLevel: Boolean = false,
    val brakeFluidLevel: Boolean = false,
    val transmissionOilLevel: Boolean = false,
    val washerFluid: Boolean = false,
    val tirePressure: Boolean = false,
    /** FR-V04 ERROR_SECURITY_PERMISSION */
    val permissionDenied: Boolean = false,
)

enum class LevelState { NORMAL, LOW, CRITICALLY_LOW, HIGH, ERROR, UNSUPPORTED }

data class FluidAlerts(
    val engineOil: LevelState = LevelState.UNSUPPORTED,
    val brakeFluid: LevelState = LevelState.UNSUPPORTED,
    val transmissionOil: LevelState = LevelState.UNSUPPORTED,
    val washerLow: Boolean = false,
)

val LevelState.isLow: Boolean get() = this == LevelState.LOW || this == LevelState.CRITICALLY_LOW

/** 계기판 경고등 중 소모품과 관련된 것만 (Safety.getTelltalesInfo — 시스템 권한 필요) */
enum class Telltale { TPMS, LOW_VOLT_BATTERY, EV_SYSTEM, LOW_EV_BATTERY, POWER_DOWN, ABS, MASTER_WARNING }

/**
 * 품목과 매칭해 화면에 보여 주는 차량 실측값. null/빈 값 = 미지원·권한 없음·아직 수신 전.
 * 계산값(LifeCalculator)을 덮어쓰지 않고 배지·상세 행으로만 표시한다 (FR-C02).
 */
data class VehicleLive(
    /** Odometer.getTripCumulativeDrivingDistance (m → km) */
    val tripCumulativeKm: Double? = null,
    /** Odometer.getTripCumulativeDrivingTime (분) */
    val tripCumulativeMinutes: Int? = null,
    /** EvBattery.getEvBatteryStateOfCharge (%) */
    val evSocPercent: Int? = null,
    /** 공기압을 읽을 수 있는 바퀴 */
    val tireWheels: Set<WheelPos> = emptySet(),
    /** Tire.getTirePressure (kPa) — PRIVATE 권한 */
    val tirePressureKpa: Map<WheelPos, Float> = emptyMap(),
    /** Tire.getLowTirePressure (kPa) */
    val tireLowThresholdKpa: Map<WheelPos, Float> = emptyMap(),
    /** Safety.hasPowerDownWarningIndicator */
    val powerDownWarning: Boolean? = null,
    /** 켜져 있는 경고등. null = 읽을 수 없음 */
    val telltales: Set<Telltale>? = null,
) {
    /** 현재 공기압 < 저압 기준이면 저압 (둘 다 있어야 판정) */
    val lowTires: Map<WheelPos, Boolean>
        get() = tireWheels.associateWith { w ->
            val cur = tirePressureKpa[w]
            val low = tireLowThresholdKpa[w]
            cur != null && low != null && cur < low
        }

    /** 누적 평균 속도 km/h — 가혹 조건(짧은 거리·정체) 판단 참고값 */
    val averageSpeedKmh: Double?
        get() {
            val km = tripCumulativeKm ?: return null
            val min = tripCumulativeMinutes?.takeIf { it > 0 } ?: return null
            return km / (min / 60.0)
        }

    fun telltaleOn(t: Telltale): Boolean = telltales?.contains(t) == true
}

/** 앱이 호출하는 Pleos Vehicle SDK API 목록 — 연동 상태 표시·문서의 기준 */
enum class VehicleApi(val api: String, val permission: String) {
    ODOMETER("Odometer.getCurrentDistanceDriven", "CAR_DRIVE"),
    TRIP_DISTANCE("Odometer.getTripCumulativeDrivingDistance", "CAR_DRIVE"),
    TRIP_TIME("Odometer.getTripCumulativeDrivingTime", "CAR_DRIVE"),
    BRAKE_FLUID("Safety.getBrakeFluidLevelState", "CAR_SAFETY"),
    TRANSMISSION_OIL("Safety.getTransmissionOilLevelState", "CAR_SAFETY"),
    ENGINE_OIL("Safety.getEngineOilLevelState", "CAR_SAFETY"),
    WASHER("Safety.hasLowWasherFluidWarning", "CAR_SAFETY"),
    POWER_DOWN("Safety.hasPowerDownWarningIndicator", "CAR_SAFETY"),
    EV_SOC("EvBattery.getEvBatteryStateOfCharge", "CAR_ENERGY"),
    TIRE_THRESHOLD("Tire.getLowTirePressure", "CAR_SAFETY"),
    TIRE_PRESSURE("Tire.getTirePressure", "PRIVATE_CAR_SAFETY"),
    TELLTALES("Safety.getTelltalesInfo", "PRIVATE_CAR_SAFETY"),
}

enum class ApiState {
    /** capability 읽기 가능 + 값 수신 */
    CONNECTED,
    /** 읽기 가능하다고 응답했지만 값은 아직/못 받음 */
    NO_VALUE,
    /** 차량·에뮬레이터가 해당 속성을 제공하지 않음 */
    UNSUPPORTED,
    /** 권한 없음 (PRIVATE_* 는 시스템 앱 전용) */
    PERMISSION_DENIED,
    /** 그 밖의 오류 */
    FAILED,
}

/** @param detail 마지막 값 요약("48213 km") 또는 오류 코드("ERROR_EMPTY_RESULT") */
data class ApiResult(val state: ApiState, val detail: String? = null)

data class VehicleInfo(val displayName: String?)

enum class FakeScenario(val label: String) {
    NORMAL("정상"),
    DRIVING("주행 증가 (+50km/2초)"),
    OIL_LOW("오일·브레이크액 레벨 저하"),
    TIRE_LOW("타이어 저압 (좌측 앞)"),
    EV_POWER_DOWN("고전압 출력 제한 경고"),
    LOW_12V("12V 배터리 경고등"),
    ODOMETER_UNSUPPORTED("Odometer 미지원"),
    PERMISSION_DENIED("권한 미승인"),
}

interface VehicleDebugControls {
    val scenario: StateFlow<FakeScenario>
    fun setScenario(scenario: FakeScenario)
    fun addKm(km: Double)
}
