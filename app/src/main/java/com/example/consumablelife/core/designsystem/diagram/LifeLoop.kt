package com.example.consumablelife.core.designsystem.diagram

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.consumablelife.core.designsystem.LocalMotion
import com.example.consumablelife.core.designsystem.LocalStatusColors
import com.example.consumablelife.core.designsystem.PlayedAnimations
import com.example.consumablelife.core.designsystem.StatusColors
import com.example.consumablelife.core.domain.Status
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 명세 v2.0 7장 — 라이프 루프(수명주기) 고리. 0%(교체 직후)에서 시계 방향으로 100%(교체 시점)까지, 다시 처음으로 이어지는 순환.
 *  DG2-L1 구간 색 0–70 양호 · 70–90 곧 교체 · 90–100 교체 시기(본문 임계값과 같음), 현재 위치 = 마커 점 + 진행 호,
 *         100% 초과분은 고리 바깥 얇은 빨간 호(최대 +25%).
 *  DG2-L2 중앙 "79%" + "사용 · 3회차"(INSPECT는 "경과"), 고리 밖 제한 요인 문구.
 *  DG2-L3 진입 시 마커 0 → 현재값 800ms. DG2-AN-12 교체 완료: 현재 → 100%(400ms) → 고리 확산 1회(200ms) → 0% 리셋, 회차 +1.
 */

/** DG2-AN-12 교체/점검 완료 애니메이션 요청 (기록 저장 직후 1회) */
@Immutable
data class LoopRefill(val key: String, val fromProgress: Float, val fromStatus: Status, val fromCycleIndex: Int)

/** 값 → 고리 구간 상태 (DG2-L1) */
fun loopStatusOf(v: Float, cautionAt: Float = 0.7f, dueAt: Float = 0.9f): Status = when {
    v >= 1f -> Status.OVERDUE
    v >= dueAt -> Status.DUE
    v >= cautionAt -> Status.CAUTION
    else -> Status.GOOD
}

@Composable
fun LifeLoop(
    progress: Float?,
    status: Status,
    cycleIndex: Int,
    estimated: Boolean,
    usageLabel: String,
    cycleText: (Int) -> String,
    footer: String?,
    modifier: Modifier = Modifier,
    cautionAt: Float = 0.7f,
    dueAt: Float = 0.9f,
    refill: LoopRefill? = null,
    size: Dp = 300.dp,
) {
    val colors: StatusColors = LocalStatusColors.current
    val motion = LocalMotion.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.surfaceVariant
    val target = (progress ?: 0f).coerceAtLeast(0f)

    val value = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    var refilling by remember(refill?.key) { mutableStateOf(refill != null && !PlayedAnimations.hasPlayed(refill.key)) }

    // DG2-AN-12 교체 완료 (DG2-L3 진입 애니메이션보다 우선)
    LaunchedEffect(refill?.key, motion) {
        val r = refill ?: return@LaunchedEffect
        if (!refilling) return@LaunchedEffect
        if (!motion.full) {
            if (motion.hardOff) { PlayedAnimations.markPlayed(r.key); refilling = false; value.snapTo(target) }
            return@LaunchedEffect
        }
        PlayedAnimations.markPlayed(r.key)
        value.snapTo(r.fromProgress.coerceIn(0f, 1.25f))
        value.animateTo(1f, tween(DiagramTimeline.REFILL_MS, easing = FastOutSlowInEasing))
        burst.snapTo(0f)
        burst.animateTo(1f, tween(DiagramTimeline.BURST_MS))
        burst.snapTo(0f)
        value.snapTo(0f)
        refilling = false
        value.animateTo(target, tween(DiagramTimeline.RING_VALUE_MS))
    }
    // DG2-L3 진입: 0 → 현재값 800ms, 이후 값 변화는 400ms (DG2-AN-11)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(target, motion, refilling) {
        if (refilling) return@LaunchedEffect
        if (!motion.full) { value.snapTo(target); entered = true; return@LaunchedEffect }
        val ms = if (entered) DiagramTimeline.RING_VALUE_MS else DiagramTimeline.LOOP_ENTER_MS
        entered = true
        value.animateTo(target, tween(ms, easing = FastOutSlowInEasing))
    }

    val shownStatus: () -> Status = { if (refilling) loopStatusOf(value.value, cautionAt, dueAt) else status }
    val shownIndex = if (refilling) refill?.fromCycleIndex ?: cycleIndex else cycleIndex

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val cx = this.size.width / 2
                val cy = this.size.height / 2
                val r = min(cx, cy) - 38.dp.toPx()
                val sw = 10.dp.toPx()
                val gap = 1.2f // 구간 사이 틈 (도)
                fun arcRect(rr: Float) = Offset(cx - rr, cy - rr) to Size(rr * 2, rr * 2)
                // 구간 트랙 (DG2-L1)
                listOf(0f to cautionAt, cautionAt to dueAt, dueAt to 1f).forEachIndexed { i, (a, b) ->
                    val col = when (i) { 0 -> colors.good; 1 -> colors.caution; else -> colors.due }
                    val (tl, sz) = arcRect(r)
                    drawArc(col.copy(alpha = 0.28f), -90f + a * 360f + gap, (b - a) * 360f - gap * 2, false, tl, sz, style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                if (progress == null && !refilling) {
                    val (tl, sz) = arcRect(r)
                    drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(sw))
                }
                // 눈금 0 / 70 / 90
                listOf(0f, cautionAt, dueAt).forEach { v ->
                    val a = (v * 360f - 90f) * PI.toFloat() / 180f
                    val i = r - 18.dp.toPx(); val o = r + 18.dp.toPx()
                    drawLine(onSurface.copy(alpha = 0.35f), Offset(cx + i * cos(a), cy + i * sin(a)), Offset(cx + o * cos(a), cy + o * sin(a)), strokeWidth = 2.dp.toPx())
                }
                val v = value.value
                val vv = v.coerceIn(0f, 1f)
                val st = shownStatus()
                val col = colors.of(st)
                if (vv > 0.001f) {
                    val (tl, sz) = arcRect(r)
                    drawArc(col, -90f, min(vv, 0.9999f) * 360f, false, tl, sz, style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                // 100% 초과분 — 바깥 얇은 빨간 호 (최대 +25%)
                if (v > 1f) {
                    val (tl, sz) = arcRect(r + 14.dp.toPx())
                    drawArc(colors.overdue, -90f, min(v - 1f, 0.25f) * 360f, false, tl, sz, style = Stroke(5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                // 마커 점
                if (progress != null || refilling) {
                    val a = (vv * 360f - 90f) * PI.toFloat() / 180f
                    val c = Offset(cx + r * cos(a), cy + r * sin(a))
                    drawCircle(col, radius = 11.dp.toPx(), center = c)
                    drawCircle(Color.White, radius = 11.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
                }
                // 고리 확산 (DG2-AN-12)
                val q = burst.value
                if (q > 0f) {
                    drawCircle(colors.good.copy(alpha = 1f - q), radius = r + 30.dp.toPx() * q, center = Offset(cx, cy), style = Stroke(6.dp.toPx()))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val v = value.value
                Text(
                    if (progress == null && !refilling) "?" else "${(v * 100).roundToInt()}%",
                    style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                )
                Text(
                    "$usageLabel · ${cycleText(shownIndex)}" + if (estimated && !refilling) " " else "",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
        if (footer != null) {
            Text(footer, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
