package com.example.consumablelife.core.designsystem.diagram

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.example.consumablelife.core.designsystem.EaseInOutSine
import com.example.consumablelife.core.designsystem.EaseOutCubic
import com.example.consumablelife.core.designsystem.EaseOutQuint
import com.example.consumablelife.core.designsystem.LocalAppColors
import com.example.consumablelife.core.designsystem.LocalMotion
import com.example.consumablelife.core.designsystem.LocalUi
import com.example.consumablelife.core.designsystem.StatusIcons
import com.example.consumablelife.core.designsystem.motionSpec
import com.example.consumablelife.core.domain.DiagramView
import com.example.consumablelife.core.domain.Status
import com.example.consumablelife.core.domain.WheelPos
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * ───────────── 설계서 v3 7장 — 핫스팟 도식 (차량 탭 메인 · 대시보드 소형 · 타임라인/S5 미니) ─────────────
 * 차량도면 폴더의 실물·투시 이미지에서 만든 투명 레이어 위에, 품목 위치를 "상태색 마커 + % 뱃지"로 얹는다.
 *  · 배경을 칠하지 않는다 (DG2-01) — 차량 탭은 bgApp, 대시보드는 카드 면이 그대로 비친다.
 *  · 마커 테두리는 항상 bgApp으로 도식과 분리 (DN-03). 유채색은 상태색과 선택(accent)뿐 (TK-01).
 *  · 모션(14장): 인트로(AN-08, 콜드 스타트 1회) 등장 → 부드러운 스캔 → 마커·뱃지 순차 등장,
 *    실물 ↔ 투시 크로스페이드(AN-05, 240ms), 선택 마커 펄스(AN-02), 확대·음영(사용자 요청 확장).
 *    스캔 경계는 딱 끊기지 않고 띠(feather)에 걸쳐 섞이며, 스캔선은 양 끝에서 서서히 나타났다 사라진다.
 *  · 애니메이션 값은 draw / offset / graphicsLayer 람다에서만 읽는다 (IM-03).
 */

@Immutable
data class HotspotUi(
    val id: String,
    val status: Status,
    /** 뱃지 숫자 "96%" / "?" */
    val percentText: String,
    val description: String,
)

/** HS-03·04 뱃지 배치: 마커 우상단 → 겹치면 우하단 → 좌상단 → 좌하단 → 그래도 겹치면 숨김(마커만, 칩으로 접근) */
object HotspotLayout {
    enum class Corner { TOP_RIGHT, BOTTOM_RIGHT, TOP_LEFT, BOTTOM_LEFT }

    @Immutable
    data class Badge(val id: String, val rect: Rect, val corner: Corner)

    /**
     * @param anchors 화면 좌표 앵커. 앵커 x가 작은 항목부터 자리를 잡고, 뒤 항목이 비켜 간다
     * @param gapX 마커 중심 → 뱃지 가까운 변, lift 마커 중심 → 위쪽 뱃지 윗변, drop 마커 중심 → 아래쪽 뱃지 윗변
     * @param markerClear 다른 마커를 가리지 않도록 비워 둘 반경
     */
    fun badges(
        anchors: List<Pair<String, Offset>>, size: Size, gapX: Float, lift: Float, drop: Float, bounds: Rect, markerClear: Float,
    ): Map<String, Badge> {
        val placed = LinkedHashMap<String, Badge>()
        anchors.sortedBy { it.second.x }.forEach { (id, a) ->
            val candidates = listOf(
                Corner.TOP_RIGHT to Offset(a.x + gapX, a.y - lift),
                Corner.BOTTOM_RIGHT to Offset(a.x + gapX, a.y + drop),
                Corner.TOP_LEFT to Offset(a.x - gapX - size.width, a.y - lift),
                Corner.BOTTOM_LEFT to Offset(a.x - gapX - size.width, a.y + drop),
            )
            val hit = candidates.firstOrNull { (_, tl) ->
                val r = Rect(tl, size)
                r.left >= bounds.left && r.right <= bounds.right && r.top >= bounds.top && r.bottom <= bounds.bottom &&
                    placed.values.none { it.rect.inflate(2f).overlaps(r) } &&
                    anchors.none { (other, o) -> other != id && r.inflate(markerClear).contains(o) }
            } ?: return@forEach
            placed[id] = Badge(id, Rect(hit.second, size), hit.first)
        }
        return placed
    }
}

/**
 * @param spots 표시할 항목(긴급도 순). 앵커가 없는 사용자 항목은 그리지 않는다
 * @param onSelect 마커·뱃지 탭(id) / 빈 곳 탭(null). null이면 탭 무반응 (S5·미니)
 * @param badges 뱃지(상태 아이콘 + %) 표시 — 차량 탭만
 * @param dimmed 필터 밖 항목: 30% 알파로 흐리게 (HS-06, 숨기지 않음)
 * @param zoom 선택 항목으로 1.6배 확대 · @param bright 선택 부위 밖을 어둡게(음영)
 * @param intro 인트로 재생 여부 (AN-08: 차량 탭에서 프로세스당 1회)
 * @param still S5: 모든 모션 off, 실물 고정
 */
@Composable
fun HotspotDiagram(
    spec: DiagramSpec,
    spots: List<HotspotUi>,
    view: DiagramView,
    selectedId: String?,
    onSelect: ((String?) -> Unit)?,
    modifier: Modifier = Modifier,
    badges: Boolean = false,
    dimmed: Set<String> = emptySet(),
    zoom: Boolean = false,
    bright: Boolean = false,
    intro: Boolean = false,
    periodicScan: Boolean = false,
    still: Boolean = false,
    animationKey: String = "hotspot",
    lowPressure: Map<WheelPos, Boolean> = emptyMap(),
    maxHeight: Dp = Dp.Unspecified,
) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val motion = LocalMotion.current
    val density = LocalDensity.current
    val cropTop = spec.compactTop
    val cropH = spec.compactHeight
    val anchored = remember(spec, spots) { spots.mapNotNull { s -> spec.hotspot(s.id)?.anchor?.let { Triple(s, spec.hotspot(s.id)!!, it) } } }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        var w = maxWidth
        val aspect = spec.width / cropH
        // 뱃지는 마커 위·아래로 도식 밖까지 나가므로 그만큼 자리를 잡아 둔다 (터치 영역 포함)
        val pad = if (badges) ui.dp(52) else 0.dp
        if (maxHeight != Dp.Unspecified) w = min(w, (maxHeight - pad * 2) * aspect)
        if (constraints.hasBoundedHeight) w = min(w, (this.maxHeight - pad * 2) * aspect)
        val h = w / aspect
        val padPx = with(density) { pad.toPx() }
        val wPx = with(density) { w.toPx() }
        val hPx = with(density) { h.toPx() }
        val sPx = wPx / spec.width // 이미지 px → 화면 px
        val layers = rememberLayers(spec, app.dark, sample = 1) // IM-02: 세 탭이 같은 비트맵을 공유

        // ── 타임라인: 인트로(1회) → 이후 최종 상태 ──
        val total = DiagramTimeline.total(anchored.size)
        val introOn = intro && !still && view == DiagramView.XRAY
        val timelineState = rememberOneShotTimeline("$animationKey:intro", total, enabled = introOn)
        val periodic by rememberPeriodicScan(animationKey, timelineState, total, periodicScan && introOn)
        val tNow: () -> Float = { if (introOn) timelineState.value else total.toFloat() }

        // AN-05 실물 ↔ 투시 크로스페이드 240ms
        val xrayMix by animateFloatAsState(
            if (view == DiagramView.XRAY && !still) 1f else 0f,
            motionSpec(tween(240, easing = EaseInOutSine)), label = "xrayMix",
        )

        // 선택 앵커 · 확대 · 음영
        val sel = anchored.firstOrNull { it.first.id == selectedId }
        val lastAnchor = remember { mutableStateOf(Offset(spec.width / 2f, cropTop + cropH / 2f)) }
        sel?.third?.let { lastAnchor.value = it }
        val focus by animateOffsetAsState(lastAnchor.value, motionSpec(tween(520, easing = EaseOutQuint)), label = "focus")
        val zoomK by animateFloatAsState(
            if (zoom && sel != null && !still) 1f else 0f, motionSpec(tween(520, easing = EaseOutQuint)), label = "zoom",
        )
        val spot by animateFloatAsState(
            if (bright && sel != null && !still) 1f else 0f, motionSpec(tween(420, easing = EaseOutCubic)), label = "spot",
        )
        // AN-02 선택 마커 펄스: halo scale 1 → 2.3, alpha .9 → 0. 무한 반복 금지(AN-R1) → 선택할 때마다 3회
        val pulse = remember { Animatable(1f) }
        LaunchedEffect(selectedId, motion.full, still) {
            pulse.snapTo(1f)
            if (selectedId == null || !motion.full || still) return@LaunchedEffect
            repeat(PULSE_REPEAT) {
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(PULSE_MS, easing = EaseOutCubic))
            }
        }

        val box = Rect(0f, 0f, wPx, hPx)
        fun base(p: Offset) = Offset(p.x * sPx, (p.y - cropTop) * sPx)
        fun view(p: Offset) = zoomTransform(base(p), base(focus), zoomK, MAX_ZOOM, box)
        val alphaOf: (String) -> Float = { id -> if (id in dimmed) DIM_ALPHA else 1f }

        val currentSelect by rememberUpdatedState(onSelect)
        val currentAnchored by rememberUpdatedState(anchored)
        val appearShift = with(density) { 10.dp.toPx() }
        val hitR = with(density) { 38.dp.toPx() } // TT-01 터치 76dp
        // 마커 반지름: 기준 20dp 지름. 작은 도식(대시보드·미니)에서는 도식 폭에 비례해 줄인다
        val markerR = with(density) { minOf((ui.dp(13) / 2).toPx(), maxOf(wPx * 0.0105f, 3.5.dp.toPx())) }
        val ringW = markerR * 0.4f
        val dimFull = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(DiagramTimeline.XRAY_EXT_BRIGHTNESS, DiagramTimeline.XRAY_EXT_BRIGHTNESS, DiagramTimeline.XRAY_EXT_BRIGHTNESS, 1f) }) }

        // HS-03·04 뱃지 자리 (화면 px, 확대 전 기준)
        val bw = ui.dp(84)
        val bh = ui.dp(38)
        val placed = remember(anchored, wPx, hPx, bw, bh, badges) {
            if (!badges) emptyMap() else with(density) {
                HotspotLayout.badges(
                    anchors = anchored.map { it.first.id to Offset(it.third.x * sPx, (it.third.y - cropTop) * sPx) },
                    size = Size(bw.toPx(), bh.toPx()),
                    gapX = ui.dp(12).toPx(), lift = ui.dp(46).toPx(), drop = ui.dp(8).toPx(),
                    bounds = Rect(0f, -ui.dp(52).toPx(), wPx, hPx + ui.dp(52).toPx()),
                    markerClear = ui.dp(9).toPx(),
                )
            }
        }
        val currentPlaced by rememberUpdatedState(placed)

        Box(Modifier.size(w, h + pad * 2)) {
            Spacer(
                Modifier
                    .offset(y = pad)
                    .size(w, h)
                    .then(
                        if (onSelect != null) Modifier.pointerInput(spec) {
                            detectTapGestures { p ->
                                val pts = currentAnchored.map { it.first.id to view(it.third) }
                                currentSelect?.invoke(DiagramLayout.hitTest(p, pts, hitR)) // HS-09 빈 곳 → 선택 해제
                            }
                        } else Modifier,
                    )
                    .drawWithCache {
                        val dstSize = IntSize(size.width.roundToInt(), (spec.height * sPx).roundToInt())
                        val dstOff = IntOffset(0, (-cropTop * sPx).roundToInt())
                        val imageRect = Rect(0f, -cropTop * sPx, size.width, (spec.height - cropTop) * sPx)
                        val wheelDash = PathEffect.dashPathEffect(floatArrayOf(8 * sPx, 8 * sPx))
                        val layerPaint = Paint()
                        onDrawBehind {
                            val L = layers ?: return@onDrawBehind // 디코딩 전: 투명
                            val t = tNow()
                            val appear = EaseOutQuint.transform((t / DiagramTimeline.APPEAR_MS).coerceIn(0f, 1f))
                            // 스캔: 선형 대신 양끝이 부드러운 곡선으로 이동
                            val sc = EaseInOutSine.transform(DiagramTimeline.scan(t))
                            val markers = DiagramTimeline.highlight(t)
                            val k = zoomScale(zoomK, MAX_ZOOM)
                            val a0 = base(focus)
                            val a1 = view(focus)
                            val xr = xrayMix
                            val scanX = size.width * (sc * (1f + FEATHER) - FEATHER * 0.2f)
                            val band = size.width * FEATHER

                            /** 실물(+투시)을 섞임 비율 m으로 그린다. m=0 실물, m=1 투시(실물 어둡게/반투명 + X-ray) */
                            fun DrawScope.body(m: Float) {
                                if (m <= 0.001f) {
                                    drawImage(L.exterior, dstOffset = dstOff, dstSize = dstSize, alpha = appear)
                                    return
                                }
                                if (app.dark) {
                                    val s = 1f - (1f - DiagramTimeline.XRAY_EXT_BRIGHTNESS) * m
                                    val f = if (m >= 0.999f) dimFull else ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(s, s, s, 1f) })
                                    drawImage(L.exterior, dstOffset = dstOff, dstSize = dstSize, alpha = appear, colorFilter = f)
                                } else {
                                    drawImage(L.exterior, dstOffset = dstOff, dstSize = dstSize, alpha = appear * (1f - (1f - DiagramTimeline.XRAY_EXT_ALPHA_DAY) * m))
                                }
                                drawImage(L.xray, dstOffset = dstOff, dstSize = dstSize, alpha = appear * m * (if (app.dark) 1f else 0.85f))
                            }
                            /** 차체 알파로 마스크 (투명 영역에 아무것도 남기지 않음) */
                            fun DrawScope.maskedByBody(block: DrawScope.() -> Unit) = drawIntoCanvas { c ->
                                c.saveLayer(imageRect, layerPaint)
                                block()
                                drawImage(L.exterior, dstOffset = dstOff, dstSize = dstSize, blendMode = BlendMode.DstIn)
                                c.restore()
                            }
                            fun DrawScope.world(block: DrawScope.() -> Unit) = withTransform({
                                translate(a1.x - a0.x, a1.y - a0.y)
                                scale(k, k, pivot = a0)
                            }, block)

                            world {
                                // 등장: 아래에서 10dp 올라오며 살짝 커진다 (0.985 → 1)
                                withTransform({
                                    translate(0f, appearShift * (1f - appear))
                                    val s0 = 0.985f + 0.015f * appear
                                    scale(s0, s0, pivot = imageRect.center)
                                }) {
                                    L.shadow?.let { drawImage(it, dstOffset = dstOff, dstSize = dstSize, alpha = appear) }
                                    if (sc >= 1f || xr <= 0.001f) {
                                        body(xr)
                                    } else {
                                        // 스캔선 왼쪽 = 투시, 오른쪽 = 실물, 그 사이 띠(band)는 여러 조각으로 나눠 부드럽게 섞는다
                                        val left = (scanX - band).coerceAtLeast(0f)
                                        if (left > 0f) clipRect(right = left) { body(xr) }
                                        if (scanX < size.width) clipRect(left = scanX.coerceAtLeast(0f)) { body(0f) }
                                        val step = band / FEATHER_STEPS
                                        for (i in 0 until FEATHER_STEPS) {
                                            val x0 = scanX - band + step * i
                                            val x1 = x0 + step + 0.5f
                                            if (x1 <= 0f || x0 >= size.width) continue
                                            val m = 1f - (i + 0.5f) / FEATHER_STEPS
                                            clipRect(left = x0.coerceAtLeast(0f), right = x1.coerceAtMost(size.width)) { body(xr * m * m * (3f - 2f * m)) }
                                        }
                                    }
                                }

                                // 음영(밝기 토글): 선택 부위 밖 차체를 어둡게 — 차체 안에서만
                                if (spot > 0f) {
                                    val r = 190f * sPx
                                    maskedByBody {
                                        drawRect(
                                            Brush.radialGradient(
                                                0f to Color.Transparent, 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f * spot),
                                                center = a0, radius = r,
                                            ),
                                            topLeft = imageRect.topLeft, size = imageRect.size,
                                        )
                                    }
                                }

                                // 인트로 스캔선: 빛 띠 + 얇은 선. 양 끝에서 서서히 나타났다 사라진다 — 차체 안에서만
                                if (sc > 0f && sc < 1f && xr > 0f) {
                                    val glow = sin(sc * PI.toFloat()).coerceIn(0f, 1f)
                                    val trail = 90f * sPx
                                    maskedByBody {
                                        drawRect(
                                            Brush.horizontalGradient(listOf(Color.Transparent, app.carInner.copy(alpha = 0.42f * glow)), startX = scanX - trail, endX = scanX),
                                            topLeft = Offset(scanX - trail, imageRect.top), size = Size(trail, imageRect.height),
                                        )
                                        drawRect(
                                            Brush.horizontalGradient(listOf(app.carInner.copy(alpha = 0.9f * glow), Color.Transparent), startX = scanX - 2 * sPx, endX = scanX + 10 * sPx),
                                            topLeft = Offset(scanX - 2 * sPx, imageRect.top), size = Size(12 * sPx, imageRect.height),
                                        )
                                    }
                                }
                                // 주기 스캔 (설정, 우 → 좌) — 차체 안에서만
                                if (periodic in 0f..1f) {
                                    val x = size.width * (1.08f - 1.16f * periodic)
                                    val trail = size.width * 0.14f
                                    val glow = sin(periodic * PI.toFloat()).coerceIn(0f, 1f)
                                    maskedByBody {
                                        drawRect(
                                            Brush.horizontalGradient(listOf(app.carInner.copy(alpha = 0.34f * glow), Color.Transparent), startX = x, endX = x + trail),
                                            topLeft = Offset(x, imageRect.top), size = Size(trail, imageRect.height),
                                        )
                                        drawLine(app.carInner.copy(alpha = 0.9f * glow), Offset(x, imageRect.top), Offset(x, imageRect.bottom), strokeWidth = 2.dp.toPx())
                                    }
                                }

                                // HS-05 바퀴 공통 항목 선택 시 4바퀴 얇은 링 / 그 밖의 추가 앵커(감속기 앞·뒤)
                                sel?.let { (s, hs, _) ->
                                    val col = app.statusColor(s.status).copy(alpha = 0.9f * markers)
                                    val wheelsHl = hs.highlight as? Highlight.Wheels
                                    if (wheelsHl != null) {
                                        val el = spec.tireEllipse ?: wheelsHl
                                        hs.allAnchors.forEach { wp ->
                                            val c = base(wp)
                                            drawOval(col, topLeft = Offset(c.x - el.rx * sPx, c.y - el.ry * sPx), size = Size(el.rx * 2 * sPx, el.ry * 2 * sPx), style = Stroke(2.dp.toPx()))
                                        }
                                    } else {
                                        hs.extraAnchors.forEach { ex -> drawCircle(col, radius = markerR * 1.5f / k, center = base(ex), style = Stroke(2.dp.toPx() / k)) }
                                    }
                                }
                                // 저압 바퀴: 점선 타원 (센서 경고는 계산값을 덮지 않는 표시, FR-C02)
                                spec.tireEllipse?.let { el ->
                                    spec.wheels.forEach { (pos, wp) ->
                                        if (lowPressure[pos] != true) return@forEach
                                        val c = base(wp)
                                        drawOval(
                                            app.status.overdue.copy(alpha = 0.95f * markers),
                                            topLeft = Offset(c.x - el.rx * sPx, c.y - el.ry * sPx), size = Size(el.rx * 2 * sPx, el.ry * 2 * sPx),
                                            style = Stroke(3.dp.toPx(), pathEffect = wheelDash),
                                        )
                                    }
                                }
                            }

                            // 마커 ↔ 뱃지 연결선: 어느 뱃지가 어느 부위인지 한눈에 (뱃지와 같은 타이밍으로 등장)
                            currentAnchored.forEachIndexed { i, (s, _, anchor) ->
                                val b = currentPlaced[s.id] ?: return@forEachIndexed
                                val isSel = s.id == selectedId
                                val a = alphaOf(s.id) * DiagramTimeline.calloutAlpha(t, i) * (if (isSel) 1f else 1f - zoomK)
                                if (a <= 0.01f) return@forEachIndexed
                                val c = view(anchor)
                                val origin = base(anchor)
                                // 뱃지 사각형에서 마커에 가장 가까운 점
                                val tx = (origin.x).coerceIn(b.rect.left, b.rect.right) - origin.x
                                val ty = (origin.y).coerceIn(b.rect.top, b.rect.bottom) - origin.y
                                val col = if (isSel) app.accent else app.statusColor(s.status)
                                drawLine(col.copy(alpha = 0.75f * a), c, Offset(c.x + tx, c.y + ty), strokeWidth = 1.5.dp.toPx())
                            }
                            // HS-02 마커: 상태색 원 + bgApp 테두리. 화면 크기 고정(확대해도 커지지 않음), 순차 등장
                            currentAnchored.forEachIndexed { i, (s, _, anchor) ->
                                val pop = EaseOutCubic.transform(((t - DiagramTimeline.HIGHLIGHT_START_MS - 35f * i) / 320f).coerceIn(0f, 1f))
                                if (pop <= 0f) return@forEachIndexed
                                val isSel = s.id == selectedId
                                val a = alphaOf(s.id) * pop
                                val c = view(anchor)
                                val col = app.statusColor(s.status)
                                val over = s.status == Status.OVERDUE || s.status == Status.SENSOR_ALERT
                                val r = markerR * (if (isSel) 1.3f else 1f) * (0.6f + 0.4f * pop) * (if (introOn && over) DiagramTimeline.pulseScale(t) else 1f)
                                if (isSel) {
                                    val p = pulse.value
                                    if (p < 1f) drawCircle(col.copy(alpha = 0.9f * (1f - p)), radius = r * (1f + 1.3f * p) + ringW, center = c, style = Stroke(ringW))
                                    else drawCircle(col.copy(alpha = 0.55f), radius = r * 1.5f + ringW, center = c, style = Stroke(ringW)) // 정지 상태 링
                                }
                                drawCircle(app.bgApp.copy(alpha = a), radius = r + ringW, center = c)
                                drawCircle(col.copy(alpha = a), radius = r, center = c)
                            }
                        }
                    },
            )

            // HS-03 뱃지: 상태 아이콘 + %. 선택 = accent 면 + 흰 글자
            if (badges) {
                anchored.forEachIndexed { i, (s, _, anchor) ->
                    val b = placed[s.id] ?: return@forEachIndexed
                    key(s.id) {
                        val isSel = s.id == selectedId
                        val col = app.statusColor(s.status)
                        val dx = b.rect.left - anchor.x * sPx
                        val dy = b.rect.top - (anchor.y - cropTop) * sPx
                        val shape = RoundedCornerShape(ui.dp(10))
                        val borderCol = when {
                            isSel -> app.accent
                            s.status == Status.GOOD || s.status == Status.UNKNOWN -> app.line2
                            else -> col
                        }
                        Row(
                            Modifier
                                .offset { view(anchor).let { IntOffset((it.x + dx).roundToInt(), (it.y + dy + padPx).roundToInt()) } }
                                .size(bw, bh)
                                .graphicsLayer {
                                    val appear = DiagramTimeline.calloutAlpha(tNow(), i)
                                    alpha = alphaOf(s.id) * appear * (if (isSel) 1f else 1f - zoomK)
                                    translationY = (1f - appear) * 6.dp.toPx()
                                }
                                .clip(shape)
                                .background(if (isSel) app.accent else app.surface)
                                .border(ui.dp(1.4f), borderCol, shape)
                                .then(if (onSelect != null) Modifier.clickable(role = Role.Button) { onSelect(s.id) } else Modifier)
                                .semantics(mergeDescendants = true) { contentDescription = s.description; selected = isSel },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(StatusIcons.of(s.status), null, tint = if (isSel) app.onAccent else col, modifier = Modifier.size(ui.dp(15)))
                            Spacer(Modifier.width(ui.dp(6)))
                            Text(
                                s.percentText, color = if (isSel) app.onAccent else app.text, maxLines = 1,
                                fontSize = ui.sp(24, 18f), fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_ZOOM = 1.6f
private const val DIM_ALPHA = 0.3f       // HS-06
private const val PULSE_MS = 1_600       // AN-02
private const val PULSE_REPEAT = 3
/** 스캔 경계가 섞이는 띠의 폭 (도식 폭 대비) */
private const val FEATHER = 0.16f
private const val FEATHER_STEPS = 14
