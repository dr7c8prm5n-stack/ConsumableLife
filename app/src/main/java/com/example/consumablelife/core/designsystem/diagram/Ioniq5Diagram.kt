package com.example.consumablelife.core.designsystem.diagram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.consumablelife.R
import com.example.consumablelife.core.designsystem.LocalMotion
import com.example.consumablelife.core.designsystem.MotionTokens
import com.example.consumablelife.core.designsystem.PlayedAnimations
import com.example.consumablelife.core.designsystem.StatusIcons
import com.example.consumablelife.core.designsystem.Tokens
import com.example.consumablelife.core.designsystem.motionSpec
import com.example.consumablelife.core.domain.DiagramView
import com.example.consumablelife.core.domain.Status
import com.example.consumablelife.core.domain.WheelPos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * ───────────── 명세 v2.0 — 아이오닉 5 투명 레이어 도식 (Ioniq5Diagram) ─────────────
 * 배경 없음(DG2-01): 이 컴포저블은 배경을 칠하지 않는다. 뒤의 앱 배경·주/야간 패널·배경화면이 그대로 비친다.
 * 레이어 (아래 → 위, 전부 투명)
 *  L1 바닥 그림자(주간만) · L2 실물(투시 영역은 야간 42% 밝기 / 주간 50% 불투명) · L3 X-ray(야간 glow / 주간 line, 스캔 영역만)
 *  L4 부위 하이라이트(원 글로우·배터리 사각형·바퀴 타원, 존별 최악 1개) · L5 리더선·앵커 · L6 콜아웃(반투명 카드)
 *  L7 스캔선(인트로 중만, 차체 알파로 마스크 — 투명 영역에 선이 뜨지 않음)
 * 모드: OVERVIEW(S1) / FOCUS(S2, 1.6배 + X-ray 렌즈) / COMPACT(콜아웃 폭 < 200dp) / GLANCE(주행 중, 실물 정적 1개)
 * 보기: XRAY(기본, 첫 진입 시 인트로) / EXTERIOR(토글, 300ms 크로스페이드 — 스캔 재생 안 함)
 * 애니메이션 값은 draw/graphicsLayer/offset 람다에서만 읽는다 (DG2-04). 비트맵은 실물·X-ray 1종·그림자 3장만 (DG2-03).
 *
 * 사용자 요청 확장(명세 외): 선택 시 [확대]·[음영 밝게] 토글, 설정에서 켜는 5초 주기 스캔(기본 꺼짐 — "무한 반복 없음" 우선).
 */

/**
 * 프로세스당 1회 재생되는 범용 타임라인 (ms). MotionPolicy NONE이면 최종 상태.
 * enabled=false면 값을 유지한 채 대기한다(나중에 켜지면 그때 1회 재생) — 호출자는 비활성일 때 최종 상태로 그린다.
 */
@Composable
fun rememberOneShotTimeline(key: String, totalMs: Int, enabled: Boolean = true): State<Float> {
    val total = totalMs.toFloat()
    val anim = remember(key) { Animatable(if (PlayedAnimations.hasPlayed(key)) total else 0f) }
    val motion = LocalMotion.current
    LaunchedEffect(key, motion, enabled) {
        when {
            anim.value >= total -> Unit
            !enabled -> Unit
            motion.full -> {
                PlayedAnimations.markPlayed(key)
                anim.animateTo(total, tween((total - anim.value).roundToInt().coerceAtLeast(1), easing = LinearEasing))
            }
            motion.hardOff -> { PlayedAnimations.markPlayed(key); anim.snapTo(total) }
            anim.value > 0f -> anim.snapTo(total)
            else -> Unit
        }
    }
    return anim.asState()
}

/** 사용자 요청 주기 스캔: 인트로 뒤 5초마다 우→좌. MotionPolicy FULL에서만 (주행 제한·백그라운드 정지) */
@Composable
internal fun rememberPeriodicScan(key: String, intro: State<Float>, introTotal: Int, enabled: Boolean): State<Float> {
    val anim = remember(key) { Animatable(-1f) }
    val motion = LocalMotion.current
    LaunchedEffect(key, motion, enabled) {
        anim.snapTo(-1f)
        if (!motion.full || !enabled) return@LaunchedEffect
        snapshotFlow { intro.value >= introTotal }.first { it }
        while (true) {
            delay((DiagramTimeline.PERIODIC_SCAN_MS - DiagramTimeline.PERIODIC_SWEEP_MS).toLong())
            anim.snapTo(0f)
            anim.animateTo(1f, tween(DiagramTimeline.PERIODIC_SWEEP_MS, easing = MotionTokens.Sweep))
            anim.snapTo(-1f)
        }
    }
    return anim.asState()
}

/* ---------- 레이어 비트맵 (DG2-A1 nodpi, DG2-03 3장 제한) ---------- */

@Immutable
internal class DiagramLayers(val exterior: ImageBitmap, val xray: ImageBitmap, val shadow: ImageBitmap?)

internal object DiagramBitmaps {
    private val cache = HashMap<String, ImageBitmap>()

    private fun key(file: String, sample: Int) = "${file.substringBeforeLast('.')}@$sample"

    @Synchronized
    fun peek(exterior: String, xray: String, shadow: String?, sample: Int): DiagramLayers? {
        val e = cache[key(exterior, sample)] ?: return null
        val x = cache[key(xray, sample)] ?: return null
        val s = shadow?.let { cache[key(it, sample)] ?: return null }
        return DiagramLayers(e, x, s)
    }

    fun load(ctx: Context, exterior: String, xray: String, shadow: String?, sample: Int): DiagramLayers? {
        val e = get(ctx, exterior, sample) ?: return null
        val x = get(ctx, xray, sample) ?: return null
        val s = shadow?.let { get(ctx, it, sample) }
        return DiagramLayers(e, x, s)
    }

    private fun get(ctx: Context, file: String, sample: Int): ImageBitmap? {
        val k = key(file, sample)
        synchronized(this) { cache[k]?.let { return it } }
        val name = file.substringBeforeLast('.')
        val id = layerRes(name) ?: ctx.resources.getIdentifier(name, "drawable", ctx.packageName).takeIf { it != 0 } ?: return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false
        }
        val bmp = BitmapFactory.decodeResource(ctx.resources, id, opts) ?: return null
        synchronized(this) {
            // 같은 레이어의 다른 샘플, 다른 X-ray 종(주간/야간)은 비운다 — 메모리에 3장만
            val xray = name.contains("xray")
            cache.keys.filter { it.startsWith("$name@") || (xray && it.contains("xray")) }.forEach { cache.remove(it) }
            return bmp.asImageBitmap().also { cache[k] = it }
        }
    }

    /** 리소스 축소(release) 대비: 명세의 레이어 파일명은 명시적으로 참조 */
    private fun layerRes(name: String): Int? = when (name) {
        "ioniq5_exterior" -> R.drawable.ioniq5_exterior
        "ioniq5_xray_glow" -> R.drawable.ioniq5_xray_glow
        "ioniq5_xray_line" -> R.drawable.ioniq5_xray_line
        "ioniq5_shadow" -> R.drawable.ioniq5_shadow
        else -> null
    }
}

@Composable
internal fun rememberLayers(spec: DiagramSpec, dark: Boolean, sample: Int): DiagramLayers? {
    val ctx = LocalContext.current.applicationContext
    val xray = if (dark) spec.layers.xrayNight else spec.layers.xrayDay
    val shadow = if (dark) null else spec.layers.shadow
    val initial = remember { DiagramBitmaps.peek(spec.layers.exterior, xray, shadow, sample) }
    val state = produceState(initialValue = initial, spec.layers, dark, sample) {
        value = DiagramBitmaps.peek(spec.layers.exterior, xray, shadow, sample)
            ?: withContext(Dispatchers.IO) { DiagramBitmaps.load(ctx, spec.layers.exterior, xray, shadow, sample) }
    }
    return state.value
}

/**
 * @param cycles 표시할 항목 (대시보드 정렬 순서). GLANCE는 최우선 1개만 넘긴다.
 * @param view XRAY(투시) / EXTERIOR(실물). GLANCE는 항상 실물, FOCUS는 실물 + 렌즈
 * @param onSelect 앵커·콜아웃 탭(id) / 빈 곳 탭(null). null이면 탭 무반응(GLANCE, DG-32)
 * @param zoomOnSelect OVERVIEW에서 선택 항목으로 확대 (사용자 요청)
 * @param brightSelect 선택 부위만 밝게, 주변 차체는 음영 (사용자 요청)
 * @param lowPressure 바퀴별 저압 경고
 * @param maxHeight 도식 최대 높이 (GLANCE: 화면 40% 이하)
 * @param compactList COMPACT 모드에서 도식 아래 상태순 목록
 */
@Composable
fun Ioniq5Diagram(
    spec: DiagramSpec,
    cycles: List<CycleUi>,
    mode: DiagramMode,
    view: DiagramView,
    selectedId: String?,
    onSelect: ((String?) -> Unit)?,
    modifier: Modifier = Modifier,
    animationKey: String = "diagram",
    zoomOnSelect: Boolean = false,
    brightSelect: Boolean = false,
    periodicScan: Boolean = false,
    lowPressure: Map<WheelPos, Boolean> = emptyMap(),
    maxHeight: Dp = Dp.Unspecified,
    compactList: @Composable (List<CycleUi>, (String) -> Unit) -> Unit = { _, _ -> },
) {
    val palette = rememberDiagramPalette()
    val motion = LocalMotion.current
    val placed = remember(spec, cycles) { DiagramLayout.assign(spec, cycles) }

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val fullAspect = spec.width.toFloat() / spec.height
        var w = maxWidth
        if (maxHeight != Dp.Unspecified) w = min(w, maxHeight * fullAspect)
        val sDp = w.value / spec.width // dp per image px
        val calloutW = (spec.slotWidth * sDp).dp
        // DG-13: 콜아웃 폭 200dp 미만이면 COMPACT
        val eff = if (mode == DiagramMode.OVERVIEW && calloutW < 200.dp) DiagramMode.COMPACT else mode
        val cropTop = if (eff == DiagramMode.COMPACT) spec.compactTop else 0f
        val imgH = if (eff == DiagramMode.COMPACT) spec.compactHeight else spec.height.toFloat()
        val h = (imgH * sDp).dp

        val density = LocalDensity.current
        val sPx = with(density) { sDp.dp.toPx() }
        val wPx = with(density) { w.toPx() }
        // DG2-03: 창이 작으면 inSampleSize=2 (앵커는 비율 계산이라 영향 없음)
        val sample = if (wPx <= spec.width / 2f) 2 else 1
        val layers = rememberLayers(spec, palette.dark, sample)

        // 인트로는 투시 보기 첫 진입(프로세스당 1회)·OVERVIEW/COMPACT에서만. 실물·FOCUS·GLANCE는 최종 상태로 그림
        val animated = eff == DiagramMode.OVERVIEW || eff == DiagramMode.COMPACT
        val introEnabled = animated && view == DiagramView.XRAY
        val total = DiagramTimeline.total(spec.slotX.size * spec.rows.size) // 항목 수와 무관한 고정 길이
        val timelineState = rememberOneShotTimeline("$animationKey:intro", total, enabled = introEnabled)
        val timeline by timelineState
        val periodic by rememberPeriodicScan(animationKey, timelineState, total, periodicScan && introEnabled)
        val tNow: () -> Float = { if (introEnabled) timeline else total.toFloat() }

        // DG2-AN-10 투시 ↔ 실물 크로스페이드 300ms (스캔 재생 안 함)
        val xrayMix by animateFloatAsState(
            if (view == DiagramView.XRAY && eff != DiagramMode.GLANCE && eff != DiagramMode.FOCUS) 1f else 0f,
            motionSpec(tween(DiagramTimeline.VIEW_FADE_MS)), label = "xrayMix",
        )

        // 선택/포커스 대상
        val focusId = if (eff == DiagramMode.GLANCE) null else selectedId
        val focusPlaced = placed.firstOrNull { it.cycle.id == focusId }
        val lastAnchor = remember { mutableStateOf(Offset(spec.width / 2f, spec.height / 2f)) }
        focusPlaced?.anchor?.let { lastAnchor.value = it }
        val spotAnchor by animateOffsetAsState(lastAnchor.value, motionSpec(tween(DiagramTimeline.FOCUS_ZOOM_MS)), label = "anchor")
        val selDim by animateFloatAsState(
            if (focusId != null && eff != DiagramMode.FOCUS) 1f else 0f,
            motionSpec(tween(DiagramTimeline.SELECT_DIM_MS)), label = "selDim",
        )
        // 부록 A DG-AN-05 포커스 줌 (FOCUS는 진입 시 0 → 1), OVERVIEW 확대 토글
        val focusZoom = remember(eff, focusId) { Animatable(if (eff == DiagramMode.FOCUS && !motion.full) 1f else 0f) }
        LaunchedEffect(eff, focusId, motion) {
            if (eff == DiagramMode.FOCUS && focusPlaced?.anchor != null) {
                if (motion.full) focusZoom.animateTo(1f, tween(DiagramTimeline.FOCUS_ZOOM_MS, easing = FastOutSlowInEasing))
                else focusZoom.snapTo(1f)
            } else focusZoom.snapTo(0f)
        }
        val selectZoom by animateFloatAsState(
            if (animated && zoomOnSelect && focusPlaced?.anchor != null) 1f else 0f,
            motionSpec(tween(DiagramTimeline.FOCUS_ZOOM_MS, easing = FastOutSlowInEasing)), label = "selZoom",
        )
        val spot by animateFloatAsState(
            if (animated && brightSelect && focusPlaced?.anchor != null) 1f else 0f,
            motionSpec(tween(DiagramTimeline.FOCUS_ZOOM_MS)), label = "spot",
        )

        // DG2-AN-11: 링 값 400ms, 하이라이트 색 300ms. 교체 완료 후 상태가 GOOD이 되면 하이라이트는 페이드아웃 (DG2-AN-12)
        val hlTargets = remember(placed, eff) { DiagramLayout.highlights(placed, includeGood = eff == DiagramMode.GLANCE).map { it.cycle.id }.toSet() }
        val ringAnims = placed.associate { p ->
            p.cycle.id to key("ring" + p.cycle.id) {
                animateFloatAsState(p.cycle.ringFill, motionSpec(tween(DiagramTimeline.RING_VALUE_MS)), label = "ring${p.cycle.id}")
            }
        }
        val hlColors = placed.associate { p ->
            p.cycle.id to key("hlc" + p.cycle.id) {
                animateColorAsState(palette.of(p.cycle.status), motionSpec(tween(DiagramTimeline.STATUS_MS)), label = "hlc${p.cycle.id}")
            }
        }
        val hlAlphas = placed.associate { p ->
            p.cycle.id to key("hla" + p.cycle.id) {
                animateFloatAsState(
                    if (p.cycle.id in hlTargets) DiagramPalette.highlightOpacity(p.cycle.status) else 0f,
                    motionSpec(tween(DiagramTimeline.STATUS_MS)), label = "hla${p.cycle.id}",
                )
            }
        }

        val boxRect = Rect(0f, 0f, wPx, with(density) { h.toPx() })
        fun zoomNow(): Float = if (eff == DiagramMode.FOCUS) focusZoom.value else selectZoom
        val maxZoom = DiagramTimeline.FOCUS_ZOOM
        /** 이미지 px → 화면 px (확대 반영). draw/layout/입력 단계에서만 호출 */
        fun base(p: Offset) = Offset(p.x * sPx, (p.y - cropTop) * sPx)
        fun view(p: Offset) = zoomTransform(base(p), base(spotAnchor), zoomNow(), maxZoom, boxRect)

        val visibleFor: (PlacedCallout) -> Boolean = { p -> eff != DiagramMode.FOCUS || p.cycle.id == focusId }
        val alphaFor: (String) -> Float = { id ->
            if (focusId == null || id == focusId) 1f else 1f - (1f - DiagramTimeline.DIM_ALPHA) * selDim
        }
        // GLANCE는 콜아웃 폭이 충분할 때만 1개 표시 (핵심 문구는 도식 밖 큰 글씨로 중복, DG-33)
        val showCallouts = eff == DiagramMode.OVERVIEW || (eff == DiagramMode.GLANCE && calloutW >= 200.dp)
        val currentSelect by rememberUpdatedState(onSelect)
        val currentPlaced by rememberUpdatedState(placed)
        val appearShiftPx = with(density) { DiagramTimeline.APPEAR_SHIFT_DP.dp.toPx() }
        val dimFull = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(DiagramTimeline.XRAY_EXT_BRIGHTNESS, DiagramTimeline.XRAY_EXT_BRIGHTNESS, DiagramTimeline.XRAY_EXT_BRIGHTNESS, 1f) }) }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 배경 없음 (DG2-01) — 둥근 clip은 확대 시 넘침만 막는다 (평소엔 투명이라 보이지 않음)
            Box(Modifier.width(w).heightIn(min = h, max = h).clip(RoundedCornerShape(Tokens.CardRadius))) {
                // ---------- L1~L5, L7: Canvas 1회 패스 ----------
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (onSelect != null) Modifier.pointerInput(eff, spec) {
                                // DG-20: 화면 기준 최소 38dp 반경, 겹치면 가장 가까운 앵커 / 빈 곳 → 선택 해제
                                detectTapGestures { p ->
                                    val anchors = currentPlaced.filter(visibleFor).flatMap { pc ->
                                        val pts = pc.hotspot?.allAnchors?.takeIf { pc.hotspot.isWheels } ?: listOfNotNull(pc.anchor)
                                        pts.map { pc.cycle.id to view(it) }
                                    }
                                    currentSelect?.invoke(DiagramLayout.hitTest(p, anchors, 38.dp.toPx()))
                                }
                            } else Modifier,
                        )
                        .drawWithCache {
                            val dstSize = IntSize(size.width.roundToInt(), (spec.height * sPx).roundToInt())
                            val dstOff = IntOffset(0, (-cropTop * sPx).roundToInt())
                            val imageRect = Rect(0f, -cropTop * sPx, size.width, (spec.height - cropTop) * sPx)
                            val dash = PathEffect.dashPathEffect(floatArrayOf(5 * sPx, 5 * sPx))
                            val reticleDash = PathEffect.dashPathEffect(floatArrayOf(10 * sPx, 8 * sPx))
                            val wheelDash = PathEffect.dashPathEffect(floatArrayOf(8 * sPx, 8 * sPx))
                            val hlBlend = if (palette.dark) BlendMode.Screen else BlendMode.Multiply
                            val lensPath = Path()
                            val layerPaint = Paint()
                            onDrawBehind {
                                val L = layers ?: return@onDrawBehind // 디코딩 전: 아무것도 그리지 않음(투명)
                                val t = tNow()
                                val appear = DiagramTimeline.appear(t)
                                val sc = DiagramTimeline.scan(t)
                                val hl = DiagramTimeline.highlight(t)
                                val z = zoomNow()
                                val k = zoomScale(z, maxZoom)
                                val a0 = base(spotAnchor)
                                val a1 = view(spotAnchor)
                                fun DrawScope.world(block: DrawScope.() -> Unit) = withTransform({
                                    translate(a1.x - a0.x, a1.y - a0.y)
                                    scale(k, k, pivot = a0)
                                }, block)
                                /** 차체 알파로 마스크해 그리기 (투명 영역에 아무것도 남기지 않음) */
                                fun DrawScope.maskedByBody(block: DrawScope.() -> Unit) = drawIntoCanvas { c ->
                                    c.saveLayer(imageRect, layerPaint)
                                    block()
                                    drawImage(L.exterior, dstOffset = dstOff, dstSize = dstSize, blendMode = BlendMode.DstIn)
                                    c.restore()
                                }
                                val xr = xrayMix
                                val scanX = size.width * sc

                                world {
                                    translate(0f, appearShiftPx * (1f - appear)) {
                                        // L1 바닥 그림자 — 주간·배경화면에서만
                                        L.shadow?.let { drawImage(it, dstOffset = dstOff, dstSize = dstSize, alpha = appear) }
                                        if (eff == DiagramMode.FOCUS) {
                                            // DG2-L5: 실물 위 선택 앵커 중심 X-ray 렌즈 (반경 150px)
                                            drawImage(L.exterior, dstOffset = dstOff, dstSize = dstSize, alpha = appear)
                                            focusPlaced?.anchor?.let { an ->
                                                val c = base(an)
                                                val r = DiagramTimeline.LENS_RADIUS * sPx
                                                lensPath.reset(); lensPath.addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r))
                                                clipPath(lensPath) {
                                                    if (palette.dark) drawImage(L.exterior, dstOffset = dstOff, dstSize = dstSize, colorFilter = dimFull)
                                                    else drawImage(L.exterior, dstOffset = dstOff, dstSize = dstSize, alpha = DiagramTimeline.XRAY_EXT_ALPHA_DAY)
                                                    drawImage(L.xray, dstOffset = dstOff, dstSize = dstSize, alpha = if (palette.dark) 1f else 0.8f)
                                                }
                                                drawCircle(palette.of(focusPlaced.cycle.status).copy(alpha = 0.9f), radius = r, center = c, style = Stroke(2.5f * sPx))
                                            }
                                        } else if (xr <= 0.001f) {
                                            // L2 실물 (실물 보기 / GLANCE)
                                            drawImage(L.exterior, dstOffset = dstOff, dstSize = dstSize, alpha = appear)
                                        } else {
                                            // L2 실물: 스캔선 오른쪽은 원래 밝기
                                            if (sc < 1f) clipRect(left = scanX) { drawImage(L.exterior, dstOffset = dstOff, dstSize = dstSize, alpha = appear) }
                                            // 왼쪽(투시): 실물 어둡게/반투명 + L3 X-ray
                                            if (sc > 0f) clipRect(right = scanX) {
                                                if (palette.dark) {
                                                    val s = 1f - (1f - DiagramTimeline.XRAY_EXT_BRIGHTNESS) * xr
                                                    val f = if (xr >= 0.999f) dimFull else ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(s, s, s, 1f) })
                                                    drawImage(L.exterior, dstOffset = dstOff, dstSize = dstSize, alpha = appear, colorFilter = f)
                                                } else {
                                                    drawImage(L.exterior, dstOffset = dstOff, dstSize = dstSize, alpha = appear * (1f - (1f - DiagramTimeline.XRAY_EXT_ALPHA_DAY) * xr))
                                                }
                                                drawImage(L.xray, dstOffset = dstOff, dstSize = dstSize, alpha = appear * xr * (if (palette.dark) 1f else 0.8f))
                                            }
                                        }
                                    }

                                    // 음영 밝게(사용자 요청): 선택 부위 밖 차체를 어둡게 + 선택 부위 빛 번짐 — 차체 안에서만
                                    if (spot > 0f) {
                                        val r = 170f * sPx
                                        maskedByBody {
                                            drawRect(
                                                Brush.radialGradient(
                                                    0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f * spot),
                                                    center = a0, radius = r,
                                                ),
                                                topLeft = imageRect.topLeft, size = imageRect.size,
                                            )
                                            drawCircle(
                                                Brush.radialGradient(listOf(Color(0xFFB8FFDC).copy(alpha = 0.32f * spot), Color.Transparent), center = a0, radius = r * 0.6f),
                                                radius = r * 0.6f, center = a0, blendMode = BlendMode.Plus,
                                            )
                                        }
                                    }

                                    // L4 부위 하이라이트 (CAUTION 이상, 존별 최악 1개) — 야간 Screen / 주간 Multiply
                                    placed.filter(visibleFor).forEach { p ->
                                        val op = hlAlphas.getValue(p.cycle.id).value
                                        if (op <= 0.001f) return@forEach
                                        val h = p.hotspot?.highlight ?: return@forEach
                                        val col = hlColors.getValue(p.cycle.id).value
                                        val a = op * hl * alphaFor(p.cycle.id)
                                        when (h) {
                                            is Highlight.Circle -> {
                                                val c = base(p.anchor!!)
                                                val rr = h.r * 1.5f * sPx
                                                drawCircle(
                                                    Brush.radialGradient(
                                                        0f to col.copy(alpha = 0.8f * a), 0.6f to col.copy(alpha = 0.25f * a), 1f to col.copy(alpha = 0f),
                                                        center = c, radius = rr,
                                                    ),
                                                    radius = rr, center = c, blendMode = hlBlend,
                                                )
                                            }
                                            is Highlight.Rect -> {
                                                val tl = base(Offset(h.x0, h.y0))
                                                val sz = Size((h.x1 - h.x0) * sPx, (h.y1 - h.y0) * sPx)
                                                val cr = CornerRadius(24 * sPx)
                                                drawRoundRect(col.copy(alpha = 0.35f * a), topLeft = tl, size = sz, cornerRadius = cr, blendMode = hlBlend)
                                                drawRoundRect(col.copy(alpha = 0.7f * a), topLeft = tl, size = sz, cornerRadius = cr, style = Stroke(3 * sPx))
                                            }
                                            is Highlight.Wheels -> {
                                                p.hotspot.allAnchors.forEach { wp ->
                                                    val c = base(wp)
                                                    drawOval(
                                                        col.copy(alpha = a), topLeft = Offset(c.x - h.rx * sPx, c.y - h.ry * sPx),
                                                        size = Size(h.rx * 2 * sPx, h.ry * 2 * sPx), style = Stroke(6 * sPx),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    // 저압 바퀴: 타이어 타원 OVERDUE 색 (센서 경고는 계산값을 덮지 않는 배지, FR-C02)
                                    spec.tireEllipse?.let { el ->
                                        spec.wheels.forEach { (pos, wp) ->
                                            if (lowPressure[pos] != true) return@forEach
                                            val c = base(wp)
                                            drawOval(
                                                palette.overdue.copy(alpha = 0.95f * hl),
                                                topLeft = Offset(c.x - el.rx * sPx, c.y - el.ry * sPx), size = Size(el.rx * 2 * sPx, el.ry * 2 * sPx),
                                                style = Stroke(5 * sPx, pathEffect = wheelDash),
                                            )
                                        }
                                    }

                                    // L7 인트로 스캔선 — 차체 안에서만 (DG2-AN-02)
                                    if (sc > 0f && sc < 1f) {
                                        val trail = 40f * sPx
                                        maskedByBody {
                                            drawRect(
                                                Brush.horizontalGradient(listOf(Color.Transparent, palette.scan.copy(alpha = 0.55f)), startX = scanX - trail, endX = scanX),
                                                topLeft = Offset(scanX - trail, imageRect.top), size = Size(trail, imageRect.height),
                                            )
                                            drawRect(palette.scan.copy(alpha = 0.95f), topLeft = Offset(scanX - 3 * sPx, imageRect.top), size = Size(6 * sPx, imageRect.height))
                                        }
                                    }
                                    // 주기 스캔 (설정, 우 → 좌, 초록 선 묶음) — 차체 안에서만
                                    if (periodic in 0f..1f) {
                                        val x = size.width * (1.08f - 1.16f * periodic)
                                        val trail = size.width * 0.12f
                                        maskedByBody {
                                            drawRect(
                                                Brush.horizontalGradient(listOf(Color.Transparent, palette.scan.copy(alpha = 0.3f)), startX = x + trail, endX = x),
                                                topLeft = Offset(x, imageRect.top), size = Size(trail, imageRect.height),
                                            )
                                            listOf(0.035f to 0.55f, 0.07f to 0.35f, 0.105f to 0.18f).forEach { (off, al) ->
                                                val lx = x + size.width * off
                                                drawLine(palette.scan.copy(alpha = al), Offset(lx, imageRect.top), Offset(lx, imageRect.bottom), strokeWidth = 1.dp.toPx())
                                            }
                                            drawLine(palette.scan, Offset(x, imageRect.top), Offset(x, imageRect.bottom), strokeWidth = 2.dp.toPx())
                                        }
                                    }
                                }

                                // L5 리더선 (OVERVIEW·GLANCE). 확대 중에는 선택 항목 선만
                                if (showCallouts) {
                                    placed.forEach { p ->
                                        val anchor = p.anchor ?: return@forEach
                                        val lp = DiagramTimeline.leader(t, p.order)
                                        if (lp <= 0f) return@forEach
                                        val isSel = p.cycle.id == focusId
                                        val la = alphaFor(p.cycle.id) * (if (isSel) 1f else 1f - z)
                                        if (la <= 0.01f) return@forEach
                                        val col = palette.of(p.cycle.status)
                                        val from = view(anchor)
                                        val to = base(DiagramLayout.leaderEnd(spec, p.row, p.slot))
                                        val end = Offset(from.x + (to.x - from.x) * lp, from.y + (to.y - from.y) * lp)
                                        drawLine(
                                            col.copy(alpha = 0.8f * la), from, end,
                                            strokeWidth = (if (isSel) 4f else 2.5f) * sPx * 1.2f, cap = StrokeCap.Round,
                                        )
                                        if (lp >= 1f) drawCircle(col.copy(alpha = la), radius = 5 * sPx * 1.2f, center = to)
                                    }
                                }

                                // L5 앵커 마커 + 레티클 + DG2-AN-05 펄스 — 앵커는 하이라이트와 함께 등장 (DG2-AN-03)
                                world {
                                    placed.filter(visibleFor).forEach { p ->
                                        val anchor = p.anchor ?: return@forEach
                                        if (hl <= 0f) return@forEach
                                        val col = palette.of(p.cycle.status)
                                        val a = alphaFor(p.cycle.id) * hl
                                        val c = base(anchor)
                                        val baseR = if (eff == DiagramMode.COMPACT) 24f else 16f
                                        val overdue = p.cycle.status == Status.OVERDUE || p.cycle.status == Status.SENSOR_ALERT
                                        val pulse = if (introEnabled && overdue) DiagramTimeline.pulseScale(t) else 1f
                                        val r = baseR * sPx * pulse
                                        val sw = (if (overdue) 6f else 4f) * sPx
                                        drawCircle(palette.anchorFill.copy(alpha = palette.anchorFill.alpha * a), radius = r, center = c)
                                        drawCircle(
                                            col.copy(alpha = a * (if (p.cycle.estimated) 0.7f else 1f)), radius = r, center = c,
                                            style = Stroke(sw.coerceAtLeast(2.dp.toPx()), pathEffect = if (p.cycle.status == Status.UNKNOWN) dash else null),
                                        )
                                        drawCircle(col.copy(alpha = a), radius = (5 * sPx).coerceAtLeast(2.5f.dp.toPx()), center = c)
                                        if (p.cycle.id == focusId) {
                                            drawCircle(col, radius = 56 * sPx, center = c, style = Stroke(2 * sPx * 1.5f, pathEffect = reticleDash))
                                        }
                                    }
                                }
                            }
                        },
                )

                // 센서 경고 차량 배지 + 바퀴 저압 배지 (확대 위치를 따라감)
                placed.filter { it.cycle.sensorAlert && it.anchor != null && visibleFor(it) }.forEach { p ->
                    key("badge" + p.cycle.id) {
                        AnchorBadge(
                            at = { view(p.anchor!!) + Offset(18 * sPx, -18 * sPx) },
                            alpha = { alphaFor(p.cycle.id) * DiagramTimeline.highlight(tNow()) },
                            icon = Icons.Filled.DirectionsCar, palette = palette,
                        )
                    }
                }
                val tireFocused = placed.any { it.hotspot?.isWheels == true && it.cycle.id == focusId }
                spec.wheels.forEach { (pos, wp) ->
                    if (lowPressure[pos] == true && (eff != DiagramMode.FOCUS || tireFocused)) {
                        key("wheel$pos") {
                            AnchorBadge(at = { view(wp) }, alpha = { DiagramTimeline.highlight(tNow()) }, icon = Icons.Filled.PriorityHigh, palette = palette)
                        }
                    }
                }

                // ---------- L6 콜아웃 ----------
                if (showCallouts) {
                    placed.forEach { p ->
                        key(p.cycle.id) {
                            val row = spec.rows.getValue(p.row)
                            CycleCallout(
                                p = p, palette = palette,
                                x = (spec.slotX[p.slot] * sDp).dp,
                                y = ((row.yTop - cropTop) * sDp).dp,
                                width = calloutW,
                                height = (row.height * sDp).dp,
                                ring = { ringAnims.getValue(p.cycle.id).value * DiagramTimeline.fill(tNow(), p.order) },
                                countUp = { DiagramTimeline.fill(tNow(), p.order) },
                                alpha = { alphaFor(p.cycle.id) * DiagramTimeline.calloutAlpha(tNow(), p.order) },
                                selected = p.cycle.id == focusId,
                                onClick = onSelect?.let { { it(p.cycle.id) } },
                            )
                        }
                    }
                }
            }
            if (eff == DiagramMode.COMPACT) {
                compactList(placed.map { it.cycle }.sortedBy { DiagramLayout.severity(it.status) }) { id -> onSelect?.invoke(id) }
            }
        }
    }
}

@Composable
private fun AnchorBadge(at: () -> Offset, alpha: () -> Float, icon: androidx.compose.ui.graphics.vector.ImageVector, palette: DiagramPalette) {
    val d = 30.dp
    val half = with(LocalDensity.current) { (d / 2).toPx() }
    Box(
        Modifier
            .offset { at().let { IntOffset((it.x - half).roundToInt(), (it.y - half).roundToInt()) } }
            .size(d)
            .graphicsLayer { this.alpha = alpha() }
            .background(palette.overdue, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color(0xFF14080A), modifier = Modifier.size(20.dp)) }
}

/** L6 콜아웃: 반투명 카드 + 사이클 링 + 3행 (DG-14: 이름 20sp, 2행 16sp, 3행 15sp) */
@Composable
private fun CycleCallout(
    p: PlacedCallout,
    palette: DiagramPalette,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    ring: () -> Float,
    countUp: () -> Float,
    alpha: () -> Float,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    val c = p.cycle
    val col = palette.of(c.status)
    val borderW = when {
        selected -> 3.dp
        c.status == Status.OVERDUE || c.status == Status.SENSOR_ALERT -> 3.dp
        c.status == Status.GOOD || c.status == Status.UNKNOWN -> 1.dp
        else -> 2.dp
    }
    val borderCol = when {
        selected -> palette.ink
        c.status == Status.GOOD -> col.copy(alpha = 0.45f)
        else -> col
    }
    val ringD = min(height - 16.dp, 64.dp)
    Box(
        Modifier
            .offset(x, y)
            .width(width)
            .heightIn(min = height, max = height)
            .graphicsLayer { this.alpha = alpha() }
            .drawBehind {
                val r = CornerRadius(18.dp.toPx())
                drawRoundRect(palette.card, cornerRadius = r)
                val bw = borderW.toPx()
                drawRoundRect(
                    borderCol, topLeft = Offset(bw / 2, bw / 2), size = Size(size.width - bw, size.height - bw), cornerRadius = r,
                    style = Stroke(bw, pathEffect = if (c.status == Status.UNKNOWN && !selected) PathEffect.dashPathEffect(floatArrayOf(12f, 12f)) else null),
                )
            }
            .clip(RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = c.description; this.selected = selected }
            .padding(horizontal = 10.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 사이클 링: 사용률로 채움, UNKNOWN은 트랙만 + "?"
            Box(
                Modifier
                    .size(ringD)
                    .drawBehind {
                        val sw = 8.dp.toPx()
                        val rr = Size(size.width - sw, size.height - sw)
                        val tl = Offset(sw / 2, sw / 2)
                        drawArc(palette.track, 0f, 360f, false, tl, rr, style = Stroke(sw))
                        val v = ring()
                        if (v > 0f) {
                            drawArc(
                                col.copy(alpha = if (c.estimated) 0.7f else 1f), -90f, 360f * v, false, tl, rr,
                                style = Stroke(sw, cap = StrokeCap.Round, pathEffect = if (c.estimated) PathEffect.dashPathEffect(floatArrayOf(14f, 7f)) else null),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val pct = c.cycleProgress
                Text(
                    if (pct == null) "?" else "${(pct * 100 * countUp()).roundToInt()}%",
                    color = palette.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.label, color = palette.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (c.sensorAlert) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.diag_sensor_badge),
                            color = Color(0xFF14080A), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            modifier = Modifier.background(palette.overdue, RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 색만으로 상태를 전달하지 않음 — 상태 아이콘 병기
                    Icon(StatusIcons.of(c.status), c.statusWord, tint = col, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(c.line2, color = col, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(c.line3, color = palette.ink2, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** COMPACT 모드 도식 아래 상태순 목록 (기본 구현). 폭이 넉넉하면 2열로 접어 토글 줄이 아래로 밀리지 않게 한다 */
@Composable
fun DiagramCompactList(items: List<CycleUi>, onClick: (String) -> Unit, statusColor: (Status) -> Color) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val cols = if (maxWidth >= 640.dp) 2 else 1
        Column(Modifier.fillMaxWidth()) {
            items.chunked(cols).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { c ->
                        Row(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .clickable(role = Role.Button) { onClick(c.id) }
                                .semantics(mergeDescendants = true) { contentDescription = c.description }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(StatusIcons.of(c.status), null, tint = statusColor(c.status), modifier = Modifier.size(22.dp))
                            Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                c.usageText?.let { "$it · ${c.statusWord}" } ?: c.statusWord,
                                style = MaterialTheme.typography.bodyMedium, color = statusColor(c.status), maxLines = 1,
                            )
                        }
                    }
                    repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
