package com.example.consumablelife.core.designsystem.diagram

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.example.consumablelife.core.domain.Status
import com.example.consumablelife.core.domain.WheelPos
import org.json.JSONArray
import org.json.JSONObject

/*
 * 명세 v2.0 4장 — 좌표계·핫스팟 데이터. 원본은 assets/diagram/ioniq5_lifecycle_hotspots.json (코드에 좌표 하드코딩 금지).
 * 좌표 단위는 기준 이미지 픽셀(1448 × 1086). 앞쪽 = 왼쪽, 운전석 = 아래쪽.
 * 파서는 본문 PresetLoader와 같은 org.json 사용.
 */

@Immutable
data class RowSpec(val yTop: Float, val height: Float)

/** DG2-C1 하이라이트 형태 */
@Immutable
sealed interface Highlight {
    /** 키가 같으면 같은 존에서 최악 1개만 (바퀴 타원은 크기가 다르면 다른 형태로 취급) */
    val shapeKey: String

    data class Circle(val r: Float) : Highlight { override val shapeKey get() = "circle" }
    data class Rect(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : Highlight { override val shapeKey get() = "rect" }
    data class Wheels(val rx: Float, val ry: Float) : Highlight { override val shapeKey get() = "wheels:${rx.toInt()}x${ry.toInt()}" }
}

@Immutable
data class CycleSpec(val kind: String, val km: Int?, val months: Int?, val firstKm: Int?, val firstMonths: Int?, val note: String?)

@Immutable
data class HotspotSpec(
    val id: String,
    val label: String,
    val shortLabel: String? = null,
    val anchor: Offset? = null,
    val row: String,
    val slot: Int,
    val highlight: Highlight? = null,
    val zone: String? = null,
    val extraAnchors: List<Offset> = emptyList(),
    val cycle: CycleSpec? = null,
    val source: String? = null,
    val approx: Boolean = false,
    val verify: Boolean = false,
    val defaultEnabled: Boolean = true,
) {
    /** 앵커 + 추가 앵커(바퀴 4개 / 감속기 앞·뒤) */
    val allAnchors: List<Offset> get() = listOfNotNull(anchor) + extraAnchors
    val isWheels: Boolean get() = highlight is Highlight.Wheels
}

/** 명세 v2.0 2장 — 투명 레이어 파일명 (res/drawable-nodpi) */
@Immutable
data class LayerSpec(val exterior: String, val xrayNight: String, val xrayDay: String, val shadow: String, val combo: String?)

@Immutable
data class DiagramSpec(
    val width: Int,
    val height: Int,
    val profileId: String,
    val vehicle: String,
    val layers: LayerSpec,
    val rows: Map<String, RowSpec>,
    val slotWidth: Float,
    val slotX: List<Float>,
    val items: List<HotspotSpec>,
    /** DG-13 COMPACT 모드 세로 잘라내기 (기본 y 170–870) */
    val compactTop: Float = 170f,
    val compactHeight: Float = 700f,
) {
    fun hotspot(id: String): HotspotSpec? = items.firstOrNull { it.id == id }

    /** 바퀴 위치: 바퀴 타원 핫스팟의 앵커 4개를 사분면으로 매핑 (앞 = 왼쪽, 운전석(왼쪽) = 아래쪽) */
    val wheels: Map<WheelPos, Offset> by lazy {
        val pts = items.firstOrNull { it.isWheels && it.allAnchors.size == 4 }?.allAnchors.orEmpty()
        pts.associateBy { p ->
            val front = p.x < width / 2f
            val left = p.y > height / 2f
            when {
                front && left -> WheelPos.FRONT_LEFT
                front -> WheelPos.FRONT_RIGHT
                left -> WheelPos.REAR_LEFT
                else -> WheelPos.REAR_RIGHT
            }
        }
    }

    /** 타이어(큰 타원) 크기 — 저압 배지·바퀴 테두리용 */
    val tireEllipse: Highlight.Wheels? get() = items.mapNotNull { it.highlight as? Highlight.Wheels }.maxByOrNull { it.rx }

    companion object {
        const val PATH = "diagram/ioniq5_lifecycle_hotspots.json"

        fun parse(json: String): DiagramSpec {
            val o = JSONObject(json)
            val img = o.getJSONObject("image")
            val layers = img.getJSONObject("layers")
            val rowsO = o.getJSONObject("rows")
            val rows = rowsO.keys().asSequence().associateWith { k ->
                rowsO.getJSONObject(k).let { RowSpec(it.getDouble("yTop").toFloat(), it.getDouble("height").toFloat()) }
            }
            val slots = o.getJSONObject("slots")
            val items = o.getJSONArray("items").objects().map { it.toHotspot() }
            val crop = o.optJSONObject("compactCrop")
            return DiagramSpec(
                width = img.getInt("width"), height = img.getInt("height"),
                profileId = o.optString("profileId"), vehicle = o.optString("vehicle"),
                layers = LayerSpec(
                    exterior = layers.getString("exterior"), xrayNight = layers.getString("xrayNight"),
                    xrayDay = layers.getString("xrayDay"), shadow = layers.getString("shadow"),
                    combo = layers.optString("combo").takeIf { it.isNotBlank() },
                ),
                rows = rows,
                slotWidth = slots.getDouble("width").toFloat(),
                slotX = slots.getJSONArray("xLeft").floats(),
                items = items,
                compactTop = crop?.optDouble("yTop", 170.0)?.toFloat() ?: 170f,
                compactHeight = crop?.optDouble("height", 700.0)?.toFloat() ?: 700f,
            )
        }

        private fun JSONObject.toHotspot(): HotspotSpec {
            val hl = optJSONObject("highlight")?.let { h ->
                when (h.getString("type")) {
                    "circle" -> Highlight.Circle(h.getDouble("r").toFloat())
                    "rect" -> Highlight.Rect(h.getDouble("x0").toFloat(), h.getDouble("y0").toFloat(), h.getDouble("x1").toFloat(), h.getDouble("y1").toFloat())
                    "wheels" -> Highlight.Wheels(h.getDouble("rx").toFloat(), h.getDouble("ry").toFloat())
                    else -> null
                }
            }
            val cy = optJSONObject("cycle")?.let { c ->
                CycleSpec(
                    kind = c.optString("kind").ifBlank { "REPLACE" },
                    km = c.optIntOrNull("km"), months = c.optIntOrNull("months"),
                    firstKm = c.optIntOrNull("firstKm"), firstMonths = c.optIntOrNull("firstMonths"),
                    note = c.optString("note").takeIf { it.isNotBlank() },
                )
            }
            return HotspotSpec(
                id = getString("id"),
                label = getString("label"),
                shortLabel = optString("shortLabel").takeIf { has("shortLabel") && it.isNotBlank() },
                anchor = optJSONArray("anchor")?.point(),
                row = getString("row"),
                slot = getInt("slot"),
                highlight = hl,
                zone = if (isNull("zone") || !has("zone")) null else getString("zone"),
                extraAnchors = optJSONArray("extraAnchors")?.let { a -> (0 until a.length()).map { a.getJSONArray(it).point() } }.orEmpty(),
                cycle = cy,
                source = optString("source").takeIf { it.isNotBlank() },
                approx = optBoolean("approx", false),
                verify = optBoolean("verify", false),
                defaultEnabled = optBoolean("defaultEnabled", true),
            )
        }

        private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
        private fun JSONArray.point() = Offset(getDouble(0).toFloat(), getDouble(1).toFloat())
        private fun JSONArray.floats() = (0 until length()).map { getDouble(it).toFloat() }
        private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

        @Volatile private var cached: DiagramSpec? = null

        /** 앱 프로세스당 1회 로드 */
        fun load(ctx: Context): DiagramSpec = cached ?: synchronized(this) {
            cached ?: parse(ctx.assets.open(PATH).bufferedReader().use { it.readText() }).also { cached = it }
        }
    }
}

@Composable
fun rememberDiagramSpec(): DiagramSpec {
    val ctx = LocalContext.current
    return remember { DiagramSpec.load(ctx.applicationContext) }
}

/**
 * 명세 v2.0 시안 팔레트 — 도식은 투명 배경 위에 그려지므로 야간(발광)·주간(짙은 선) 두 벌.
 * 콜아웃 카드는 반투명(L6): 야간 rgba(3,16,12,.86), 주간 rgba(255,255,255,.92).
 */
@Immutable
data class DiagramPalette(
    val good: Color, val caution: Color, val due: Color, val overdue: Color, val unknown: Color,
    val ink: Color, val ink2: Color, val card: Color, val track: Color, val scan: Color, val anchorFill: Color,
    /** 야간이면 Screen, 주간이면 Multiply 블렌드 (시안 L4) */
    val dark: Boolean,
) {
    fun of(s: Status): Color = when (s) {
        Status.GOOD -> good
        Status.CAUTION -> caution
        Status.DUE -> due
        Status.OVERDUE, Status.SENSOR_ALERT -> overdue
        Status.UNKNOWN -> unknown
    }

    companion object {
        val Dark = DiagramPalette(
            good = Color(0xFF3DFFB0), caution = Color(0xFFFFB547), due = Color(0xFFFF7A45), overdue = Color(0xFFFF4D5E),
            unknown = Color(0xFF9AA5B4), ink = Color(0xFFE8FFF6), ink2 = Color(0xFF9FC9B8),
            card = Color(0xDB03100C), track = Color(0x24FFFFFF), scan = Color(0xFF3DFFB0), anchorFill = Color(0x8C000000),
            dark = true,
        )
        val Light = DiagramPalette(
            good = Color(0xFF0B7A52), caution = Color(0xFF9A5800), due = Color(0xFFB4400C), overdue = Color(0xFFC21F2F),
            unknown = Color(0xFF5F6673), ink = Color(0xFF10221B), ink2 = Color(0xFF4A665A),
            card = Color(0xEBFFFFFF), track = Color(0x1F10221B), scan = Color(0xFF0F8F6A), anchorFill = Color(0xCCFFFFFF),
            dark = false,
        )

        /** 시안 L4 하이라이트 불투명도: CAUTION .55, DUE .75, OVERDUE .95 (GOOD은 주행 중 1개 표시 때만) */
        fun highlightOpacity(s: Status): Float = when (s) {
            Status.CAUTION -> 0.55f
            Status.DUE -> 0.75f
            Status.OVERDUE, Status.SENSOR_ALERT -> 0.95f
            Status.GOOD -> 0.45f
            Status.UNKNOWN -> 0f
        }
    }
}

/** DG2-02: 앱 테마가 어두우면 발광 레이어(야간 팔레트), 밝으면 선 레이어(주간 팔레트) */
@Composable
fun rememberDiagramPalette(): DiagramPalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) DiagramPalette.Dark else DiagramPalette.Light
}
