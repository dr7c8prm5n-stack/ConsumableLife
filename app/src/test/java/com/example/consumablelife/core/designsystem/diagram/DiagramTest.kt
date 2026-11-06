package com.example.consumablelife.core.designsystem.diagram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.example.consumablelife.core.designsystem.Tokens
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.Status
import com.example.consumablelife.core.domain.WheelPos
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.pow

/** 명세 v2.0 11장 수용 기준 중 순수 로직으로 검증 가능한 항목 */
class DiagramTest {
    private val spec = DiagramSpec.parse(File("src/main/assets/diagram/ioniq5_lifecycle_hotspots.json").readText())

    private fun cycle(id: String, status: Status, p: Float? = 0.5f) = CycleUi(
        id = id, name = id, status = status, cycleProgress = p, cycleIndex = 1, estimated = false,
        kind = CycleKind.REPLACE, userDefault = false, usageLabel = "사용",
        usageText = null, line2 = "", line3 = "", factorText = null, statusWord = "", sensorAlert = false, description = id,
    )

    @Test fun `JSON 파싱 - 1448x1086, 레이어 5종, 10개 품목, 하이라이트 형태, 바퀴 사분면`() {
        assertThat(spec.width).isEqualTo(1448)
        assertThat(spec.height).isEqualTo(1086)
        assertThat(spec.profileId).isEqualTo("IONIQ5_LIFECYCLE")
        assertThat(spec.layers.exterior).isEqualTo("ioniq5_exterior.webp")
        assertThat(spec.layers.xrayNight).isEqualTo("ioniq5_xray_glow.webp")
        assertThat(spec.layers.xrayDay).isEqualTo("ioniq5_xray_line.webp")
        assertThat(spec.layers.shadow).isEqualTo("ioniq5_shadow.webp")
        assertThat(spec.slotX).hasSize(5)
        assertThat(spec.items.map { it.id }).containsExactly(
            "BATTERY_12V", "WIPER", "CABIN_FILTER", "HV_BATTERY", "TIRE_ROTATION",
            "COOLANT", "BRAKE_PAD", "BRAKE_FLUID", "REDUCER_OIL", "TIRE_REPLACE",
        ).inOrder()
        assertThat(spec.hotspot("BATTERY_12V")!!.highlight).isEqualTo(Highlight.Circle(70f))
        assertThat(spec.hotspot("HV_BATTERY")!!.highlight).isEqualTo(Highlight.Rect(715f, 355f, 1010f, 690f))
        assertThat(spec.hotspot("TIRE_ROTATION")!!.highlight).isEqualTo(Highlight.Wheels(100f, 48f))
        assertThat(spec.hotspot("BRAKE_PAD")!!.highlight).isEqualTo(Highlight.Wheels(46f, 30f)) // DG2-C1 타이어와 구분
        assertThat(spec.hotspot("REDUCER_OIL")!!.allAnchors).containsExactly(Offset(1180f, 520f), Offset(250f, 520f))
        assertThat(spec.hotspot("COOLANT")!!.cycle!!.firstKm).isEqualTo(200_000)
        assertThat(spec.hotspot("BATTERY_12V")!!.approx).isTrue()
        assertThat(spec.tireEllipse).isEqualTo(Highlight.Wheels(100f, 48f))
        // 앞 = 왼쪽, 운전석(왼쪽) = 아래쪽
        assertThat(spec.wheels[WheelPos.FRONT_LEFT]).isEqualTo(Offset(335f, 775f))
        assertThat(spec.wheels[WheelPos.FRONT_RIGHT]).isEqualTo(Offset(335f, 268f))
        assertThat(spec.wheels[WheelPos.REAR_LEFT]).isEqualTo(Offset(1120f, 775f))
        assertThat(spec.wheels[WheelPos.REAR_RIGHT]).isEqualTo(Offset(1120f, 268f))
    }

    @Test fun `11장 10개 품목 콜아웃의 리더선이 교차하지 않는다`() {
        val cycles = spec.items.map { cycle(it.id, Status.GOOD) }
        val placed = DiagramLayout.assign(spec, cycles)
        assertThat(placed).hasSize(10)
        val segs = placed.filter { it.anchor != null }.map { it.anchor!! to DiagramLayout.leaderEnd(spec, it.row, it.slot) }
        for (i in segs.indices) for (j in i + 1 until segs.size) {
            assertThat(DiagramLayout.segmentsCross(segs[i].first, segs[i].second, segs[j].first, segs[j].second)).isFalse()
        }
        assertThat(DiagramLayout.segmentsCross(Offset(0f, 0f), Offset(10f, 10f), Offset(0f, 10f), Offset(10f, 0f))).isTrue()
    }

    @Test fun `DG-11·12 숨긴 항목 슬롯은 비우고, 사용자 항목은 남는 슬롯에 리더선 없이`() {
        val ids = listOf("BATTERY_12V", "COOLANT", "CUSTOM_1", "CUSTOM_2")
        val placed = DiagramLayout.assign(spec, ids.map { cycle(it, Status.GOOD) })
        val bat = placed.first { it.cycle.id == "BATTERY_12V" }
        assertThat(bat.row to bat.slot).isEqualTo("top" to 0) // JSON 고정 슬롯 유지 (숨김 품목 슬롯을 당기지 않음)
        val c1 = placed.first { it.cycle.id == "CUSTOM_1" }
        val c2 = placed.first { it.cycle.id == "CUSTOM_2" }
        assertThat(c1.anchor).isNull(); assertThat(c2.anchor).isNull()
        assertThat(c1.row to c1.slot).isEqualTo("bottom" to 1) // 아래 행의 첫 빈 슬롯
        assertThat(c1.row to c1.slot).isNotEqualTo(c2.row to c2.slot)
        assertThat(placed.map { it.row to it.slot }.toSet()).hasSize(4) // 슬롯 중복 없음
        assertThat(placed.map { it.order }).containsExactly(0, 1, 2, 3)
    }

    @Test fun `DG-20 탭 판정 - 반경 안 가장 가까운 앵커, 밖이면 null`() {
        val anchors = listOf("A" to Offset(100f, 100f), "B" to Offset(140f, 100f))
        assertThat(DiagramLayout.hitTest(Offset(125f, 100f), anchors, 38f)).isEqualTo("B")
        assertThat(DiagramLayout.hitTest(Offset(110f, 100f), anchors, 38f)).isEqualTo("A")
        assertThat(DiagramLayout.hitTest(Offset(300f, 300f), anchors, 38f)).isNull()
    }

    @Test fun `L4 하이라이트 - CAUTION 이상만, 같은 존·같은 형태는 최악 1개, 바퀴 타원은 크기가 다르면 둘 다`() {
        val cycles = listOf(
            cycle("BATTERY_12V", Status.DUE), cycle("BRAKE_FLUID", Status.CAUTION), cycle("COOLANT", Status.GOOD), // 앞 모터룸 원 3개 → 12V만
            cycle("TIRE_REPLACE", Status.DUE), cycle("TIRE_ROTATION", Status.GOOD), cycle("BRAKE_PAD", Status.CAUTION), // 바퀴: 타이어 + 패드
            cycle("HV_BATTERY", Status.GOOD), cycle("WIPER", Status.OVERDUE),
        )
        val placed = DiagramLayout.assign(spec, cycles)
        assertThat(DiagramLayout.highlights(placed).map { it.cycle.id }).containsExactly("BATTERY_12V", "TIRE_REPLACE", "BRAKE_PAD", "WIPER")
        // 주행 중 1개 표시는 GOOD도 하이라이트
        assertThat(DiagramLayout.highlights(DiagramLayout.assign(spec, listOf(cycle("HV_BATTERY", Status.GOOD))), includeGood = true)).hasSize(1)
    }

    private fun lum(c: Color): Double {
        fun ch(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun over(fg: Color, bg: Color): Color = Color(
        fg.red * fg.alpha + bg.red * (1 - fg.alpha), fg.green * fg.alpha + bg.green * (1 - fg.alpha), fg.blue * fg.alpha + bg.blue * (1 - fg.alpha),
    )

    @Test fun `L6 반투명 콜아웃 카드 위 글자·상태색 대비 4_5 이상 (야간·주간)`() {
        listOf(DiagramPalette.Dark to Tokens.NightBackdrop[1], DiagramPalette.Light to Tokens.DayBackdrop[1]).forEach { (p, backdrop) ->
            val card = lum(over(p.card, backdrop))
            listOf(p.good, p.caution, p.due, p.overdue, p.unknown, p.ink, p.ink2).forEach {
                val (hi, lo) = listOf(lum(it), card).sortedDescending()
                assertThat((hi + 0.05) / (lo + 0.05)).isAtLeast(4.5)
            }
        }
        assertThat(DiagramPalette.highlightOpacity(Status.OVERDUE)).isEqualTo(0.95f)
        assertThat(DiagramPalette.highlightOpacity(Status.UNKNOWN)).isEqualTo(0f)
    }

    @Test fun `6_1 인트로 타임라인 - 등장 400 → 스캔 400~1300 → 하이라이트 1300~1700 → 콜아웃 1450+50n → 펄스 2300~2900`() {
        assertThat(DiagramTimeline.appear(0f)).isEqualTo(0f)
        assertThat(DiagramTimeline.appear(400f)).isEqualTo(1f)
        assertThat(DiagramTimeline.scan(400f)).isEqualTo(0f)
        assertThat(DiagramTimeline.scan(850f)).isWithin(1e-4f).of(0.5f) // Linear
        assertThat(DiagramTimeline.scan(1_300f)).isEqualTo(1f)
        assertThat(DiagramTimeline.highlight(1_300f)).isEqualTo(0f)
        assertThat(DiagramTimeline.highlight(1_700f)).isEqualTo(1f)
        assertThat(DiagramTimeline.fill(1_450f, 0)).isEqualTo(0f)
        assertThat(DiagramTimeline.fill(1_450f, 1)).isEqualTo(0f) // 슬롯 1은 50ms 뒤
        assertThat(DiagramTimeline.fill(2_150f, 0)).isEqualTo(1f)
        assertThat(DiagramTimeline.fill(2_150f + 50f * 9, 9)).isEqualTo(1f)
        assertThat(DiagramTimeline.leader(1_450f + 700f * 0.45f, 0)).isEqualTo(1f)
        assertThat(DiagramTimeline.calloutAlpha(1_450f + 700f / 3f, 0)).isWithin(1e-4f).of(1f)
        assertThat(DiagramTimeline.pulseScale(2_300f)).isEqualTo(1f)
        assertThat(DiagramTimeline.pulseScale(2_450f)).isWithin(1e-4f).of(1.2f) // 1회째 최대
        assertThat(DiagramTimeline.pulseScale(2_600f)).isWithin(1e-4f).of(1f)   // 1회 왕복 끝
        assertThat(DiagramTimeline.pulseScale(2_750f)).isWithin(1e-4f).of(1.2f) // 2회째 최대
        assertThat(DiagramTimeline.pulseScale(2_900f)).isEqualTo(1f)           // 2회 후 정지
        assertThat(DiagramTimeline.total(10)).isEqualTo(2_900)
        assertThat(DiagramTimeline.PERIODIC_SCAN_MS).isEqualTo(5_000)
    }

    @Test fun `DG2-L1 라이프 루프 구간 - 70·90·100 경계`() {
        assertThat(loopStatusOf(0.5f)).isEqualTo(Status.GOOD)
        assertThat(loopStatusOf(0.7f)).isEqualTo(Status.CAUTION)
        assertThat(loopStatusOf(0.9f)).isEqualTo(Status.DUE)
        assertThat(loopStatusOf(1.0f)).isEqualTo(Status.OVERDUE)
        assertThat(loopStatusOf(0.75f, cautionAt = 0.8f, dueAt = 0.95f)).isEqualTo(Status.GOOD)
    }

    @Test fun `DG-AN-05 확대 - 앵커 중심 1_6배, 이미지가 영역을 계속 덮음`() {
        val rect = Rect(0f, 0f, 1448f, 1086f)
        val anchor = Offset(150f, 520f) // 냉각수 (가장자리)
        assertThat(zoomTransform(anchor, anchor, 0f, 1.6f, rect)).isEqualTo(anchor)
        val tl = zoomTransform(rect.topLeft, anchor, 1f, 1.6f, rect)
        val br = zoomTransform(rect.bottomRight, anchor, 1f, 1.6f, rect)
        assertThat(tl.x).isAtMost(0f); assertThat(tl.y).isAtMost(0f)
        assertThat(br.x).isAtLeast(rect.right); assertThat(br.y).isAtLeast(rect.bottom)
        assertThat(zoomScale(1f, 1.6f)).isEqualTo(1.6f)
    }
}
