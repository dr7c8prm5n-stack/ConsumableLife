package com.example.consumablelife.core.domain

import java.time.LocalDate

/* 순수 Kotlin 도메인 모델 — Android 의존성 없음 (FR-C05) */

enum class Powertrain { ICE, HEV, EV }

enum class Condition { NORMAL, SEVERE }

enum class DistanceUnit { KM, MILE }

/** 선언 순서 = 대시보드 정렬 순서 (FR-C04). SENSOR_ALERT는 오버레이지만 정렬 키로 사용 */
enum class Status { OVERDUE, SENSOR_ALERT, DUE, CAUTION, GOOD, UNKNOWN }

enum class Factor { DISTANCE, TIME }

/**
 * 품목 ↔ 차량 실측 API 연결 (프리셋 JSON의 sensor 필드).
 *  ENGINE_OIL_LEVEL/BRAKE_FLUID_LEVEL/ATF_LEVEL = Safety 오일 레벨, WASHER = 워셔액 부족, TIRE = 공기압·TPMS,
 *  EV_POWER = 출력 제한(Power Down)·EV 시스템 경고, LOW_VOLT_BATTERY = 12V 배터리 경고등
 */
enum class SensorLink { ENGINE_OIL_LEVEL, BRAKE_FLUID_LEVEL, ATF_LEVEL, WASHER, TIRE, EV_POWER, LOW_VOLT_BATTERY }

/**
 * 명세 v2.0 3장 — 주기 종류.
 *  REPLACE 정기 교체 · INSPECT 정기 점검(교체 아님, "경과/점검" 어휘) · USER 정기표에 없어 상태로 판단(기본값을 사용자가 수정)
 */
enum class CycleKind { REPLACE, INSPECT, USER }

/** 명세 v2.0 10장 — 기록의 성격 (INSPECT 품목은 "점검 완료") */
enum class RecordAction { REPLACED, INSPECTED }

/** 명세 v2.0 8장 — 도식 보기 (DataStore 저장) */
enum class DiagramView { XRAY, EXTERIOR }

enum class RecordSource {
    /** 차량 Odometer 값 그대로 기록 */
    VEHICLE_ODO,
    /** 사용자가 날짜/거리 입력 */
    MANUAL,
    /** 온보딩 "모름 → 지금부터 추적" (추정 표기) */
    ESTIMATED,
}

/** 차량 도식에서 항목이 위치하는 구역 (상세 "위치" 문구 + 하이라이트 존 묶음) */
enum class CarZone { ENGINE_BAY, WINDSHIELD, CABIN, UNDERBODY, DRIVETRAIN, WHEELS, OTHER }

enum class WheelPos { FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT }

/** 거리·기간 주기. 둘 다 null이면 "주기 없음"(예: ATF 통상 조건 무교체) */
data class Interval(val km: Int? = null, val months: Int? = null) {
    val isEmpty: Boolean get() = km == null && months == null
}

data class ConsumableItem(
    val id: String,
    val customName: String?,
    val iconKey: String,
    val normal: Interval,
    val severe: Interval,
    /** 냉각수 "최초" 주기 — 실제 교체 기록이 0건일 때 적용 (DG2-I3) */
    val first: Interval?,
    val enabled: Boolean,
    val sortOrder: Int,
    val powertrains: Set<Powertrain>,
    val sensorLink: SensorLink?,
    val zone: CarZone,
    val needsConfirm: Boolean = false,
    /** 주기 종류 (명세 v2.0 10장) */
    val cycleKind: CycleKind = CycleKind.REPLACE,
    /** USER 품목 기본값 배지 — 사용자가 주기를 바꾸면 false (DG2-I2) */
    val isUserDefault: Boolean = false,
    /** 가혹 조건에서 종류가 바뀌는 품목 (감속기 오일: INSPECT → REPLACE 120,000 km) */
    val severeKind: CycleKind? = null,
) {
    val isCustom: Boolean get() = id.startsWith(CUSTOM_PREFIX)

    /** 운행 조건과 교체 횟수에 따른 활성 주기 (FR-I02, 15.1 #8) */
    fun activeInterval(condition: Condition, realReplacementCount: Int): Interval {
        if (first != null && realReplacementCount == 0) return first
        return if (condition == Condition.SEVERE) severe else normal
    }

    /** 운행 조건에 따른 활성 주기 종류 */
    fun activeKind(condition: Condition): CycleKind =
        if (condition == Condition.SEVERE && severeKind != null) severeKind else cycleKind

    fun appliesTo(powertrain: Powertrain?): Boolean = powertrain == null || powertrain in powertrains

    companion object {
        const val CUSTOM_PREFIX = "CUSTOM_"
        const val MAX_CUSTOM = 5
    }
}

data class ReplacementRecord(
    val id: Long,
    val itemId: String,
    val odometerKm: Double,
    val date: LocalDate,
    val source: RecordSource,
    val memo: String?,
    val action: RecordAction = RecordAction.REPLACED,
)

/** 상태 판정 임계값 (6.1, 설정에서 변경 가능) */
data class Thresholds(
    val cautionRatio: Double = 0.30,
    val dueRatio: Double = 0.10,
    val cautionKm: Int = 1500,
    val cautionDays: Int = 30,
)

/** 차량 실측 경고 (FR-C02: 계산값을 덮어쓰지 않는 배지) */
sealed interface SensorAlert {
    data object EngineOilLow : SensorAlert
    data object BrakeFluidLow : SensorAlert
    data object TransmissionOilLow : SensorAlert
    data object WasherFluidLow : SensorAlert
    /** wheels가 비어 있으면 TPMS 경고등만 켜진 상태(바퀴 위치 미상) */
    data class TirePressureLow(val wheels: Set<WheelPos>) : SensorAlert
    /** 고전압 시스템 출력 제한(Power Down) 또는 EV 시스템 경고등 */
    data object EvPowerLimited : SensorAlert
    /** 12V 보조배터리 경고등 */
    data object LowVoltBattery : SensorAlert
}
