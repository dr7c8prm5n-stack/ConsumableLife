package com.example.consumablelife.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.ElectricCar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.TireRepair
import androidx.compose.material.icons.outlined.TripOrigin
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.consumablelife.core.domain.Status

/**
 * 수리자재 아이콘 — 가는 선(Outlined) 계열로 통일해 차분한 브랜드 톤. 텍스트 라벨 병기 (11장 SHOULD).
 * 색은 항목이 아니라 "상태"가 전달하도록: 아이콘은 주색(Hyundai/Active Blue) 단색 + 연한 배경 배지(IconBadge).
 * 키는 프리셋 JSON의 icon 필드 (아이오닉 5 EV 10개).
 */
object ItemIcons {
    fun of(iconKey: String): ImageVector = when (iconKey) {
        "battery" -> Icons.Outlined.BatteryChargingFull
        "wiper" -> Icons.Outlined.Waves
        "filter" -> Icons.Outlined.AcUnit
        "ev" -> Icons.Outlined.ElectricCar
        "tire" -> Icons.Outlined.TireRepair
        "tire_wear" -> Icons.Outlined.TripOrigin
        "coolant" -> Icons.Outlined.Thermostat
        "brake" -> Icons.Outlined.Adjust
        "fluid" -> Icons.Outlined.WaterDrop
        "gear" -> Icons.Outlined.Settings
        else -> Icons.Outlined.Tune
    }
}

/** UX-04 상태 아이콘: 체크·시계·렌치·느낌표·차량·물음표 (6.1) — 상태는 채운(Filled) 형태로 한눈에 */
object StatusIcons {
    fun of(status: Status): ImageVector = when (status) {
        Status.GOOD -> Icons.Filled.CheckCircle
        Status.CAUTION -> Icons.Filled.Schedule
        Status.DUE -> Icons.Filled.Build
        Status.OVERDUE -> Icons.Filled.Error
        Status.SENSOR_ALERT -> Icons.Filled.DirectionsCar
        Status.UNKNOWN -> Icons.Filled.HelpOutline
    }
}
