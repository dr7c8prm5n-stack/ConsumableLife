package com.example.consumablelife.core.designsystem

import androidx.compose.ui.graphics.Color
import com.example.consumablelife.core.vehicle.UxRestrictionState
import com.example.consumablelife.feature.record.RecordCheck
import com.example.consumablelife.feature.record.validateRecord
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import kotlin.math.pow

class DesignPolicyTest {

    private fun lum(c: Color): Double {
        fun ch(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** UX-10: 상태색 텍스트·아이콘 대비 4.5:1 이상 (카드 배경 위, 상태칩 위) */
    @Test fun `UX-10 상태색 대비 4_5 이상 - 주간·야간`() {
        val cases = listOf(Tokens.DayStatus to Tokens.DaySurface, Tokens.NightStatus to Tokens.NightSurface)
        cases.forEach { (st, surface) ->
            listOf(st.good, st.caution, st.due, st.overdue, st.unknown).forEach { c ->
                assertThat(contrast(c, surface)).isAtLeast(4.5)
                assertThat(contrast(st.onStatus, c)).isAtLeast(4.5)
            }
        }
        assertThat(contrast(Tokens.DayOnSurface, Tokens.DaySurface)).isAtLeast(4.5)
        assertThat(contrast(Tokens.NightOnSurface, Tokens.NightSurface)).isAtLeast(4.5)
    }

    @Test fun `10_1 MotionPolicy - DO, 배율 0, 비활성이면 NONE`() {
        val free = UxRestrictionState.Unrestricted
        assertThat(motionPolicy(free, 1f, true)).isEqualTo(MotionPolicy.FULL)
        assertThat(motionPolicy(UxRestrictionState.FullyRestricted, 1f, true)).isEqualTo(MotionPolicy.NONE)
        assertThat(motionPolicy(free, 0f, true)).isEqualTo(MotionPolicy.NONE)
        assertThat(motionPolicy(free, 1f, false)).isEqualTo(MotionPolicy.NONE)
        assertThat(motionOf(free, 1f, false).hardOff).isFalse()
        assertThat(motionOf(free, 0f, true).hardOff).isTrue()
    }

    @Test fun `AN-01 stagger - 최대 8개, 이후 즉시`() {
        assertThat(MotionTokens.timelineMs(20)).isEqualTo(700 + 50 * 7)
        assertThat(MotionTokens.staggerFraction(0f, 0)).isEqualTo(0f)
        assertThat(MotionTokens.staggerFraction(700f, 0)).isEqualTo(1f)
        assertThat(MotionTokens.staggerFraction(700f, 1)).isLessThan(1f)
        assertThat(MotionTokens.staggerFraction(0f, 8)).isEqualTo(1f)
    }

    /** AC-P: 무한 반복 애니메이션이 코드에 존재하지 않는다 (정적 검사) */
    @Test fun `AN-R1 infiniteRepeatable 사용 0건`() {
        val src = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertThat(src).isNotEmpty()
        val hits = src.filter { f -> f.readLines().any { it.contains("infiniteRepeatable") || it.contains("rememberInfiniteTransition") } }
        assertThat(hits).isEmpty()
    }

    /** AC-P: manifest Pleos 퍼미션은 읽기 전용 4개뿐 (CONTROL_*·PRIVATE_* 없음) */
    @Test fun `AC-P manifest Pleos 퍼미션 - 읽기 전용 4개`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val perms = Regex("""uses-permission android:name="(pleos\.car\.permission\.[A-Z_]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertThat(perms).containsExactly(
            "pleos.car.permission.CAR_DRIVE", "pleos.car.permission.CAR_SAFETY", "pleos.car.permission.CAR_INFO",
            "pleos.car.permission.CAR_ENERGY",
        )
        assertThat(perms.none { it.contains("CONTROL_") || it.contains("PRIVATE_") }).isTrue()
        assertThat(manifest).doesNotContain("android.permission.INTERNET") // NFR-05
        assertThat(manifest).contains("com.hmg.rotary.ROTARY_SUPPORTED")
        assertThat(manifest).doesNotContainMatch("""<meta-data\s+android:name="distractionOptimized"""") // MVP DO 미선언
    }

    @Test fun `FR-R03 교체 기록 검증`() {
        val today = LocalDate.of(2026, 11, 3)
        assertThat(validateRecord(48_000.0, today.plusDays(1), today, 48_213.0, null)).isEqualTo(RecordCheck.FutureDate)
        assertThat(validateRecord(48_300.0, today, today, 48_213.0, null)).isInstanceOf(RecordCheck.OverOdometer::class.java)
        assertThat(validateRecord(40_000.0, today, today, 48_213.0, 45_000.0)).isInstanceOf(RecordCheck.BeforePrevious::class.java)
        assertThat(validateRecord(48_213.0, today, today, 48_213.0, 45_000.0)).isEqualTo(RecordCheck.Ok)
    }
}
