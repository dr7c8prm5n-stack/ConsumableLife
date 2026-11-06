package com.example.consumablelife.core.designsystem.diagram

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.Status
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** 부록 A 7장 / 명세 v2.0 8장 화면 모드 */
enum class DiagramMode { OVERVIEW, FOCUS, COMPACT, GLANCE }

/** 도식 UI 모델 — DiagramMapper가 제공 (DG-01: 화면에서 수치를 계산하지 않음) */
@Immutable
data class CycleUi(
    val id: String,
    /** 사용자 항목 등 핫스팟에 이름이 없을 때 쓰는 이름 */
    val name: String,
    /** 색 판정용 표시 상태 (센서 경고 포함) */
    val status: Status,
    /** 1.0 초과 가능, UNKNOWN = null */
    val cycleProgress: Float?,
    val cycleIndex: Int,
    val estimated: Boolean,
    /** 주기 종류 (INSPECT는 "경과/점검" 어휘, USER는 기본값 배지) */
    val kind: CycleKind,
    /** DG2-I2 "기본값 · 수정 가능" 배지 */
    val userDefault: Boolean,
    /** 링 라벨 "사용" / "경과" */
    val usageLabel: String,
    /** "사용 79%" (UNKNOWN = null) */
    val usageText: String?,
    /** 2행: "사용 79% · 3회차" 또는 상태어 */
    val line2: String,
    /** 3행: 제한 요인 기준 남은 값 / "+340 km 초과" / "점검까지 11,800 km" (USER는 "기본 · " 접두) */
    val line3: String,
    /** 라이프 루프 아래 제한 요인 문구 ("거리 기준 · 3,200 km 남음") */
    val factorText: String?,
    val statusWord: String,
    val sensorAlert: Boolean,
    /** DG-23 접근성 문구 */
    val description: String,
) {
    val ringFill: Float get() = (cycleProgress ?: 0f).coerceIn(0f, 1f)
    val percentText: String get() = cycleProgress?.let { "${(it * 100).toInt()}%" } ?: "?"
}

/** 슬롯에 배치된 콜아웃 */
@Immutable
data class PlacedCallout(
    val cycle: CycleUi,
    val hotspot: HotspotSpec?,
    val row: String,
    val slot: Int,
    /** 이미지 좌표 앵커 (사용자 항목은 null → 리더선 없음, DG-12) */
    val anchor: Offset?,
    /** 순서 = JSON 슬롯 순서 (stagger 인덱스) */
    val order: Int,
) {
    /** 1행 짧은 이름: 핫스팟 shortLabel → label, 앵커 없는 사용자 항목은 사용자가 정한 이름 */
    val label: String get() = if (hotspot?.anchor != null) hotspot.shortLabel ?: hotspot.label else cycle.name
}

object DiagramLayout {
    val ROW_ORDER = listOf("top", "bottom")

    /**
     * DG-10~12: 핫스팟 항목은 JSON의 고정 슬롯, 숨김 항목 슬롯은 비워 둠(당기지 않음).
     * 앵커 없는 사용자 항목은 남는 슬롯(앵커 없는 핫스팟 슬롯 → 아래 행 → 위 행 순)에 리더선 없이.
     */
    fun assign(spec: DiagramSpec, cycles: List<CycleUi>): List<PlacedCallout> {
        val byId = cycles.associateBy { it.id }
        val placed = mutableListOf<PlacedCallout>()
        val used = mutableSetOf<Pair<String, Int>>()
        spec.items.filter { it.anchor != null }.forEach { h ->
            val c = byId[h.id] ?: return@forEach
            placed += PlacedCallout(c, h, h.row, h.slot, h.anchor, 0)
            used += h.row to h.slot
        }
        val reserved = spec.items.filter { it.anchor == null }.map { it.row to it.slot }
        val all = ROW_ORDER.reversed().flatMap { r -> spec.slotX.indices.map { r to it } }
        val free = (reserved + all).distinct().filter { it !in used }.toMutableList()
        cycles.filter { c -> spec.items.none { it.anchor != null && it.id == c.id } }.forEach { c ->
            val slot = free.removeFirstOrNull() ?: return@forEach
            placed += PlacedCallout(c, spec.hotspot(c.id), slot.first, slot.second, null, 0)
        }
        // 슬롯 순서(위 행 → 아래 행, 왼쪽 → 오른쪽)로 stagger 인덱스
        return placed
            .sortedWith(compareBy({ ROW_ORDER.indexOf(it.row) }, { it.slot }))
            .mapIndexed { i, p -> p.copy(order = i) }
    }

    /** 리더선 끝점 = 콜아웃 가장자리 중앙 (이미지 좌표) */
    fun leaderEnd(spec: DiagramSpec, row: String, slot: Int): Offset {
        val r = spec.rows.getValue(row)
        val x = spec.slotX[slot] + spec.slotWidth / 2
        return Offset(x, if (row == "top") r.yTop + r.height else r.yTop)
    }

    /** DG-20 탭 판정: 반경(화면 px) 안에서 가장 가까운 앵커 */
    fun hitTest(p: Offset, anchors: List<Pair<String, Offset>>, minRadiusPx: Float): String? =
        anchors.map { (id, a) -> id to (a - p).getDistance() }
            .filter { it.second <= minRadiusPx }
            .minByOrNull { it.second }?.first

    /**
     * L4 하이라이트 대상: CAUTION 이상(주행 중 1개 표시는 GOOD 포함) 품목 중 같은 존·같은 형태면 최악 1개만 (부록 A 6장 규칙 유지).
     */
    fun highlights(placed: List<PlacedCallout>, includeGood: Boolean = false): List<PlacedCallout> =
        placed.filter { p ->
            p.anchor != null && p.hotspot?.highlight != null &&
                (severity(p.cycle.status) <= 2 || (includeGood && p.cycle.status == Status.GOOD))
        }
            .groupBy { p -> p.hotspot?.zone?.let { "$it:${p.hotspot.highlight!!.shapeKey}" } ?: p.cycle.id }
            .map { (_, l) -> l.minBy { severity(it.cycle.status) } }

    fun severity(s: Status): Int = when (s) {
        Status.OVERDUE, Status.SENSOR_ALERT -> 0
        Status.DUE -> 1
        Status.CAUTION -> 2
        Status.GOOD -> 3
        Status.UNKNOWN -> 4
    }

    /** 두 선분 교차 여부 (리더선 교차 검증용) */
    fun segmentsCross(a1: Offset, a2: Offset, b1: Offset, b2: Offset): Boolean {
        fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val d1 = cross(b1, b2, a1)
        val d2 = cross(b1, b2, a2)
        val d3 = cross(a1, a2, b1)
        val d4 = cross(a1, a2, b2)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }
}

/**
 * 확대 변환: 앵커를 확대 진행도 z(0..1)에 따라 영역 중앙 쪽으로 옮기며 maxZoom 배까지 키운다.
 * 이미지가 영역을 항상 덮도록 이동량을 제한한다.
 */
fun zoomTransform(p: Offset, anchor: Offset, z: Float, maxZoom: Float, rect: Rect): Offset {
    if (z <= 0f) return p
    val s = zoomScale(z, maxZoom)
    val c = rect.center
    val tx = (anchor.x + (c.x - anchor.x) * z).coerceIn(rect.right - (rect.right - anchor.x) * s, rect.left + (anchor.x - rect.left) * s)
    val ty = (anchor.y + (c.y - anchor.y) * z).coerceIn(rect.bottom - (rect.bottom - anchor.y) * s, rect.top + (anchor.y - rect.top) * s)
    return Offset(tx + (p.x - anchor.x) * s, ty + (p.y - anchor.y) * s)
}

fun zoomScale(z: Float, maxZoom: Float) = 1f + (maxZoom - 1f) * z

/**
 * 명세 v2.0 6.1 인트로 타임라인 (ms, 프로세스당 1회). 하나의 진행값 t에서 모든 단계를 파생한다.
 *  DG2-AN-01 0–400 실물 등장 · DG2-AN-02 400–1300 스캔(좌→우) · DG2-AN-03 1300–1700 하이라이트·앵커
 *  DG2-AN-04 1450–2300 리더선·콜아웃·링(슬롯 순 50ms) · DG2-AN-05 2300–2900 OVERDUE 펄스 2회
 */
object DiagramTimeline {
    const val APPEAR_MS = 400
    const val SCAN_START_MS = 400
    const val SCAN_MS = 900
    const val HIGHLIGHT_START_MS = 1_300
    const val HIGHLIGHT_MS = 400
    const val CALLOUT_START_MS = 1_450
    const val CALLOUT_MS = 700
    const val STAGGER_MS = 50
    const val PULSE_START_MS = 2_300
    const val PULSE_MS = 600          // 300ms × 2 왕복
    const val TOTAL_MS = 2_900
    /** 실물 등장 시 아래→위 이동량 (dp) */
    const val APPEAR_SHIFT_DP = 8f

    const val FOCUS_ZOOM_MS = 400    // 부록 A DG-AN-05
    const val FOCUS_ZOOM = 1.6f
    /** DG2-L5 FOCUS X-ray 렌즈 반경 (이미지 px) */
    const val LENS_RADIUS = 150f
    const val SELECT_DIM_MS = 200    // DG-21
    const val DIM_ALPHA = 0.28f      // L6
    const val VIEW_FADE_MS = 300     // DG2-AN-10 투시 ↔ 실물 크로스페이드
    const val STATUS_MS = 300        // DG2-AN-11 하이라이트 색
    const val RING_VALUE_MS = 400    // DG2-AN-11 링 값
    const val LOOP_ENTER_MS = 800    // DG2-L3 마커 0 → 현재값
    const val REFILL_MS = 400        // DG2-AN-12 현재 → 100%
    const val BURST_MS = 200         // DG2-AN-12 고리 확산
    /** 야간 투시 상태의 실물 밝기 (ColorMatrix scale) · 주간 투시 상태의 실물 불투명도 */
    const val XRAY_EXT_BRIGHTNESS = 0.42f
    const val XRAY_EXT_ALPHA_DAY = 0.5f

    /** 사용자 요청 CM-05 주기 스캔 (설정에서 켤 때만) */
    const val PERIODIC_SCAN_MS = 5_000
    const val PERIODIC_SWEEP_MS = 1_400

    /** 항목이 많으면 마지막 콜아웃이 끝날 때까지 연장 */
    fun total(count: Int) = max(TOTAL_MS, CALLOUT_START_MS + STAGGER_MS * (count - 1).coerceAtLeast(0) + CALLOUT_MS)

    private fun ease(k: Float) = FastOutSlowInEasing.transform(k.coerceIn(0f, 1f))

    /** DG2-AN-01 실물 등장 0..1 */
    fun appear(t: Float) = ease(t / APPEAR_MS)

    /** DG2-AN-02 스캔선 위치 0..1 (좌 → 우, Linear) */
    fun scan(t: Float) = ((t - SCAN_START_MS) / SCAN_MS).coerceIn(0f, 1f)

    /** DG2-AN-03 하이라이트·앵커 페이드인 0..1 */
    fun highlight(t: Float) = ease((t - HIGHLIGHT_START_MS) / HIGHLIGHT_MS)

    private fun callout(t: Float, order: Int) = ((t - CALLOUT_START_MS - STAGGER_MS * order) / CALLOUT_MS).coerceIn(0f, 1f)

    /** 링 채움·카운트업 0..1 (FastOutSlowIn 700ms, 슬롯 순 50ms) */
    fun fill(t: Float, order: Int) = ease(callout(t, order))

    /** 리더선 트림 0..1 (콜아웃 구간의 앞 43%에 그려짐 — 시안) */
    fun leader(t: Float, order: Int) = (callout(t, order) * 2.3f).coerceIn(0f, 1f)

    /** 콜아웃 페이드 0..1 (앞 33%) */
    fun calloutAlpha(t: Float, order: Int) = (callout(t, order) * 3f).coerceIn(0f, 1f)

    /** DG2-AN-05 OVERDUE 앵커 반지름 배율: 1 → 1.2 왕복 2회 후 1 */
    fun pulseScale(t: Float): Float {
        val k = (t - PULSE_START_MS) / PULSE_MS
        if (k <= 0f || k >= 1f) return 1f
        return 1f + 0.2f * abs(sin(k * PI.toFloat() * 2f))
    }
}
