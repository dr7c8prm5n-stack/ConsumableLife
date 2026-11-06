package com.example.consumablelife.core.data

import android.content.Context
import com.example.consumablelife.core.domain.CarZone
import com.example.consumablelife.core.domain.ConsumableItem
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.Interval
import com.example.consumablelife.core.domain.Powertrain
import com.example.consumablelife.core.domain.SensorLink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** 프리셋 = 명세 v2.0의 차량 프로필 (품목·주기) */
data class Preset(
    val profileId: String, val vehicle: String, val items: List<ConsumableItem>,
    /** 같은 프로필 안의 개정 번호 — 바뀌면 차량 API 연결(sensor)만 다시 맞춘다 */
    val rev: Int = 1,
)

/**
 * 품목·주기 프리셋 파서. 원본은 assets/diagram/ioniq5_lifecycle_hotspots.json 하나 — 좌표(DiagramSpec)와 주기(여기)를 같은 파일에서 읽는다 (명세 v2.0 0장).
 * 콜드 스타트 경로에서는 호출하지 않음 (NFR-01) — 온보딩/초기화/프로필 동기화 때만.
 */
@Singleton
class PresetLoader @Inject constructor(@ApplicationContext private val ctx: Context) {

    suspend fun load(): Preset = withContext(Dispatchers.IO) {
        val json = ctx.assets.open(PATH).bufferedReader().use { it.readText() }
        parse(json)
    }

    companion object {
        const val PATH = "diagram/ioniq5_lifecycle_hotspots.json"

        fun parse(json: String): Preset {
            val root = JSONObject(json)
            val arr = root.getJSONArray("items")
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val cycle = o.getJSONObject("cycle")
                val kind = CycleKind.valueOf(cycle.getString("kind"))
                val normal = cycle.interval("km", "months")
                val first = cycle.interval("firstKm", "firstMonths").takeIf { !it.isEmpty }
                val severeO = o.optJSONObject("severe")
                val severe = severeO?.interval("km", "months")?.takeIf { !it.isEmpty } ?: normal
                val pts = o.optJSONArray("powertrains")
                ConsumableItem(
                    id = o.getString("id"),
                    customName = null,
                    iconKey = o.optString("icon").ifBlank { "custom" },
                    normal = normal,
                    severe = severe,
                    first = first,
                    enabled = o.optBoolean("defaultEnabled", true),
                    sortOrder = i,
                    powertrains = pts?.let { a -> (0 until a.length()).map { Powertrain.valueOf(a.getString(it)) }.toSet() }
                        ?: Powertrain.entries.toSet(),
                    sensorLink = o.optString("sensor").takeIf { it.isNotBlank() }?.let { SensorLink.valueOf(it) },
                    zone = o.optString("zone").takeIf { it.isNotBlank() }?.let { runCatching { CarZone.valueOf(it) }.getOrNull() } ?: CarZone.OTHER,
                    needsConfirm = o.optBoolean("verify", false),
                    cycleKind = kind,
                    isUserDefault = kind == CycleKind.USER,
                    severeKind = severeO?.optString("kind")?.takeIf { it.isNotBlank() }?.let { CycleKind.valueOf(it) },
                )
            }
            return Preset(
                profileId = root.optString("profileId").ifBlank { "DEFAULT" },
                vehicle = root.optString("vehicle"),
                items = items,
                rev = root.optInt("presetRev", 1),
            )
        }

        private fun JSONObject.interval(kmKey: String, monthsKey: String) = Interval(
            km = if (has(kmKey) && !isNull(kmKey)) getInt(kmKey) else null,
            months = if (has(monthsKey) && !isNull(monthsKey)) getInt(monthsKey) else null,
        )
    }
}
