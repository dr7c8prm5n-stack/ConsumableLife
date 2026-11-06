package com.example.consumablelife.core.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.consumablelife.core.vehicle.UxRestrictionState

/* 10.1 MotionPolicy — 모든 애니메이션 스펙은 이 헬퍼를 경유한다. */

enum class MotionPolicy { FULL, NONE }

@Immutable
data class Motion(
    val policy: MotionPolicy,
    /** DO 또는 시스템 애니메이션 배율 0 → 앞으로도 재생하지 않음(최종값 스냅) */
    val hardOff: Boolean,
) {
    val full: Boolean get() = policy == MotionPolicy.FULL
}

fun motionPolicy(ux: UxRestrictionState, systemAnimScale: Float, isResumed: Boolean): MotionPolicy =
    if (ux.requiresDistractionOptimization || systemAnimScale == 0f || !isResumed) MotionPolicy.NONE
    else MotionPolicy.FULL

fun motionOf(ux: UxRestrictionState, systemAnimScale: Float, isResumed: Boolean) = Motion(
    policy = motionPolicy(ux, systemAnimScale, isResumed),
    hardOff = ux.requiresDistractionOptimization || systemAnimScale == 0f,
)

fun <T> spec(policy: MotionPolicy, full: AnimationSpec<T>): AnimationSpec<T> =
    if (policy == MotionPolicy.FULL) full else snap()

val LocalMotion = staticCompositionLocalOf { Motion(MotionPolicy.NONE, hardOff = false) }

@Composable
fun <T> motionSpec(full: AnimationSpec<T>): AnimationSpec<T> = spec(LocalMotion.current.policy, full)

/**
 * AN-01 "프로세스당 화면별 1회" 기록. 프로세스 수명 동안 유지(AN-R2: 복귀 시 재생 안 함).
 */
object PlayedAnimations {
    private val played = mutableSetOf<String>()
    fun hasPlayed(key: String) = key in played
    fun markPlayed(key: String) { played += key }
}

object MotionTokens {
    const val SWEEP_MS = 700          // AN-01
    const val STAGGER_MS = 50         // AN-01 카드 간
    const val STAGGER_MAX = 8         // 최대 8개까지만 stagger
    const val VALUE_MS = 400          // AN-03
    const val COLOR_MS = 300          // AN-03
    const val CHECK_MS = 200          // AN-04
    const val PULSE_MS = 300          // AN-05
    const val NAV_MS = 250            // AN-06
    const val REORDER_MS = 300        // AN-07
    const val CAR_DRAW_MS = 650       // CM-01 차체 외곽선
    const val CAR_SCAN_MS = 900       // CM-02 점검 스캔
    const val ZOOM_MS = 450           // CM-06 선택 항목 확대·음영
    val Sweep = FastOutSlowInEasing
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** AN-01 전체 타임라인 길이 */
    fun timelineMs(count: Int): Int = SWEEP_MS + STAGGER_MS * (minOf(count, STAGGER_MAX) - 1).coerceAtLeast(0)

    /** 타임라인(ms) → i번째 카드의 진행률 0..1 (8번째 이후는 즉시 1) */
    fun staggerFraction(timelineMs: Float, index: Int): Float {
        if (index >= STAGGER_MAX) return 1f
        val local = ((timelineMs - index * STAGGER_MS) / SWEEP_MS).coerceIn(0f, 1f)
        return Sweep.transform(local)
    }
}
