package com.example.consumablelife.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.consumablelife.core.domain.Condition
import com.example.consumablelife.core.domain.DiagramView
import com.example.consumablelife.core.domain.DistanceUnit
import com.example.consumablelife.core.domain.Powertrain
import com.example.consumablelife.core.domain.Thresholds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val drivingCondition: Condition = Condition.NORMAL,
    val powertrain: Powertrain? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val cautionRatio: Float = 0.30f, val dueRatio: Float = 0.10f,
    val cautionKm: Int = 1500, val cautionDays: Int = 30,
    /** Odometer 미지원/권한 없음 시 사용 */
    val manualOdometerKm: Double? = null,
    /** NFR-01 콜드 스타트 즉시 표시용 마지막 차량 주행거리 */
    val lastVehicleOdometerKm: Double? = null,
    val onboardingDone: Boolean = false,
    /** 부록 A 외 사용자 요청: 도식 5초 주기 스캔 (기본 꺼짐 — "무한 반복 없음" 우선) */
    val periodicScan: Boolean = false,
    /** 명세 v2.0 8장 — 도식 보기(투시/실물) 토글 저장 */
    val diagramView: DiagramView = DiagramView.XRAY,
    /** 적재된 품목 프로필 (프리셋 JSON profileId). 다르면 프리셋을 다시 적재 */
    val profileId: String? = null,
    /** 적용된 프리셋 개정 번호 (차량 API 연결 갱신용) */
    val presetRev: Int = 1,
) {
    val thresholds: Thresholds
        get() = Thresholds(cautionRatio.toDouble(), dueRatio.toDouble(), cautionKm, cautionDays)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(@ApplicationContext private val ctx: Context) {

    private object K {
        val condition = stringPreferencesKey("condition")
        val powertrain = stringPreferencesKey("powertrain")
        val unit = stringPreferencesKey("unit")
        val cautionRatio = floatPreferencesKey("cautionRatio")
        val dueRatio = floatPreferencesKey("dueRatio")
        val cautionKm = intPreferencesKey("cautionKm")
        val cautionDays = intPreferencesKey("cautionDays")
        val manualOdo = doublePreferencesKey("manualOdo")
        val lastOdo = doublePreferencesKey("lastVehicleOdo")
        val lastOdoSavedAt = doublePreferencesKey("lastVehicleOdoSavedAt")
        val onboardingDone = booleanPreferencesKey("onboardingDone")
        val periodicScan = booleanPreferencesKey("periodicScan")
        val diagramView = stringPreferencesKey("diagramView")
        val profileId = stringPreferencesKey("profileId")
        val presetRev = intPreferencesKey("presetRev")
        val notified = stringSetPreferencesKey("notifiedStatuses")
    }

    private fun Preferences.toSettings() = AppSettings(
        drivingCondition = this[K.condition]?.let { runCatching { Condition.valueOf(it) }.getOrNull() } ?: Condition.NORMAL,
        powertrain = this[K.powertrain]?.let { runCatching { Powertrain.valueOf(it) }.getOrNull() },
        distanceUnit = this[K.unit]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: DistanceUnit.KM,
        cautionRatio = this[K.cautionRatio] ?: 0.30f,
        dueRatio = this[K.dueRatio] ?: 0.10f,
        cautionKm = this[K.cautionKm] ?: 1500,
        cautionDays = this[K.cautionDays] ?: 30,
        manualOdometerKm = this[K.manualOdo],
        lastVehicleOdometerKm = this[K.lastOdo],
        onboardingDone = this[K.onboardingDone] ?: false,
        periodicScan = this[K.periodicScan] ?: false,
        diagramView = this[K.diagramView]?.let { runCatching { DiagramView.valueOf(it) }.getOrNull() } ?: DiagramView.XRAY,
        profileId = this[K.profileId],
        presetRev = this[K.presetRev] ?: 1,
    )

    val settings: Flow<AppSettings> = ctx.dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        ctx.dataStore.edit { p ->
            val cur = p.toSettings()
            val n = transform(cur)
            p[K.condition] = n.drivingCondition.name
            n.powertrain?.let { p[K.powertrain] = it.name } ?: p.remove(K.powertrain)
            p[K.unit] = n.distanceUnit.name
            p[K.cautionRatio] = n.cautionRatio
            p[K.dueRatio] = n.dueRatio
            p[K.cautionKm] = n.cautionKm
            p[K.cautionDays] = n.cautionDays
            n.manualOdometerKm?.let { p[K.manualOdo] = it } ?: p.remove(K.manualOdo)
            n.lastVehicleOdometerKm?.let { p[K.lastOdo] = it } ?: p.remove(K.lastOdo)
            p[K.onboardingDone] = n.onboardingDone
            p[K.periodicScan] = n.periodicScan
            p[K.diagramView] = n.diagramView.name
            n.profileId?.let { p[K.profileId] = it } ?: p.remove(K.profileId)
            p[K.presetRev] = n.presetRev
        }
    }

    suspend fun saveLastVehicleOdometer(km: Double) {
        ctx.dataStore.edit { p ->
            p[K.lastOdo] = km
            p[K.lastOdoSavedAt] = System.currentTimeMillis().toDouble()
        }
    }

    /** FR-N01: 항목별 마지막 알림 상태 ("BATTERY_12V=CAUTION") */
    suspend fun notifiedStatuses(): Map<String, String> =
        ctx.dataStore.data.first()[K.notified].orEmpty()
            .mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()

    suspend fun setNotifiedStatuses(map: Map<String, String>) {
        ctx.dataStore.edit { p -> p[K.notified] = map.map { "${it.key}=${it.value}" }.toSet() }
    }

    suspend fun clear() {
        ctx.dataStore.edit { it.clear() }
    }
}
