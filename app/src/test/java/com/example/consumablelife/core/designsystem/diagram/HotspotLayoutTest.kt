package com.example.consumablelife.core.designsystem.diagram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.example.consumablelife.core.designsystem.Ui
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/** 설계서 v3 7.2 HS-03·04 뱃지 배치와 4장 치수 배율 */
class HotspotLayoutTest {
    private val size = Size(84f, 38f)
    private val bounds = Rect(0f, -52f, 640f, 360f)

    private fun place(vararg a: Pair<String, Offset>) =
        HotspotLayout.badges(a.toList(), size, gapX = 12f, lift = 46f, drop = 8f, bounds = bounds, markerClear = 9f)

    @Test fun `HS-03 뱃지는 마커 우상단에 놓인다`() {
        val b = place("A" to Offset(100f, 150f)).getValue("A")
        assertThat(b.corner).isEqualTo(HotspotLayout.Corner.TOP_RIGHT)
        assertThat(b.rect.left).isEqualTo(112f)
        assertThat(b.rect.top).isEqualTo(104f)
    }

    @Test fun `HS-04 겹치면 앵커 x가 큰 항목이 우하단으로 내려간다`() {
        // B의 우상단 자리는 A의 뱃지와 겹친다 (B 마커 자체는 A 뱃지 아래로 벗어나 있음)
        val r = place("A" to Offset(100f, 150f), "B" to Offset(150f, 170f))
        assertThat(r.getValue("A").corner).isEqualTo(HotspotLayout.Corner.TOP_RIGHT)
        assertThat(r.getValue("B").corner).isEqualTo(HotspotLayout.Corner.BOTTOM_RIGHT)
        assertThat(r.getValue("A").rect.overlaps(r.getValue("B").rect)).isFalse()
    }

    @Test fun `HS-04 오른쪽 끝에서는 왼쪽으로, 자리가 없으면 숨긴다`() {
        assertThat(place("A" to Offset(620f, 150f)).getValue("A").corner).isEqualTo(HotspotLayout.Corner.TOP_LEFT)
        // 같은 자리에 5개가 몰리면 4개 모서리만 채우고 나머지는 뱃지 없이 마커만
        val crowded = (0 until 5).map { "P$it" to Offset(300f + it, 150f) }.toTypedArray()
        assertThat(place(*crowded).size).isAtMost(4)
    }

    @Test fun `아이오닉 5 품목 10개 - 놓인 뱃지끼리 겹치지 않고 다른 마커를 가리지 않는다`() {
        val spec = DiagramSpec.parse(File("src/main/assets/diagram/ioniq5_lifecycle_hotspots.json").readText())
        val s = 640f / spec.width
        val anchors = spec.items.map { it.id to Offset(it.anchor!!.x * s, (it.anchor!!.y - spec.compactTop) * s) }
        val placed = HotspotLayout.badges(anchors, size, 12f, 46f, 8f, Rect(0f, -52f, 640f, spec.compactHeight * s + 52f), 9f)
        assertThat(placed.size).isAtLeast(7) // 대부분 표시 (나머지는 도크 칩으로 접근)
        val rects = placed.values.map { it.rect }
        for (i in rects.indices) for (j in i + 1 until rects.size) assertThat(rects[i].overlaps(rects[j])).isFalse()
        placed.values.forEach { b -> anchors.filter { it.first != b.id }.forEach { (_, o) -> assertThat(b.rect.contains(o)).isFalse() } }
    }

    @Test fun `4장 치수 배율 - 기준 창에서 목업 px x 1_5625, 작은 창은 비율 축소, 글자는 하한 유지`() {
        val ref = Ui.forWindow(Ui.REF_W, Ui.REF_H)
        assertThat(ref.scale).isEqualTo(1f)
        assertThat(ref.dp(64).value).isWithin(0.01f).of(100f)   // 헤더 100dp
        assertThat(ref.dp(52).value).isWithin(0.3f).of(81f)     // CTA 80dp
        assertThat(ref.display.fontSize.value).isEqualTo(48f)
        val small = Ui.forWindow(1200f, 700f)
        assertThat(small.scale).isWithin(0.001f).of(700f / Ui.REF_H) // 세로가 더 빠듯 → 세로 기준
        assertThat(small.caption.fontSize.value).isAtLeast(16f)      // UX-12 보조 16sp 하한
        assertThat(small.number.fontSize.value).isAtLeast(26f)
    }
}
