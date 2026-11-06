package com.example.consumablelife.core.designsystem.diagram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.consumablelife.core.designsystem.LocalStatusColors
import com.example.consumablelife.core.designsystem.animatedPercent
import com.example.consumablelife.core.domain.CycleSpan

/*
 * 부록 A 10장 — 상세(S2) 사이클 이력 타임라인.
 *  DG-40 막대 길이 = 그 사이클에 실제 쓴 양(km 또는 개월). 권장 주기 위치에 세로 점선, 초과분은 OVERDUE 색으로 이어 그림.
 *  DG-41 진행 중 회차는 사선 패턴 + "진행 중". 기간 기준 항목은 개월 축.
 */

/** 막대 1개의 표시값 (km 또는 개월) */
data class CycleBar(
    val span: CycleSpan,
    val value: Double,
    val title: String,
    val range: String,
    val valueText: String,
    /** 권장 주기 초과분 "(+0.5개월)" — 둘째 줄에 OVERDUE 색으로 */
    val overText: String? = null,
)

@Composable
fun CycleHistoryChart(
    bars: List<CycleBar>,
    recommended: Double,
    recommendedLabel: String,
    currentLabel: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalStatusColors.current
    val good = colors.good
    val over = colors.overdue
    val track = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val maxV = maxOf(recommended * 1.15, bars.maxOfOrNull { it.value } ?: 0.0) * 1.08
    val reveal by animatedPercent(100f) // AN-01 재사용: 최초 1회 0 → 100%

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        bars.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(150.dp)) {
                    Text(
                        b.title + if (b.span.current) " · $currentLabel" else "",
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(b.range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .drawWithCache {
                            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                            val hatch = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()))
                            onDrawBehind {
                                val barH = 28.dp.toPx()
                                val top = (size.height - barH) / 2
                                val px = { v: Double -> (v / maxV * size.width).toFloat() }
                                val r = CornerRadius(6.dp.toPx())
                                drawRoundRect(track, topLeft = Offset(0f, top), size = Size(size.width, barH), cornerRadius = r)
                                val v = b.value * reveal / 100f
                                val ok = px(minOf(v, recommended))
                                val ov = px(v) - ok
                                val fill = if (b.span.estimated) good.copy(alpha = 0.7f) else good
                                drawRoundRect(fill, topLeft = Offset(0f, top), size = Size(ok, barH), cornerRadius = r)
                                if (b.span.current) {
                                    // DG-41 사선 패턴: 바 안을 사선으로 긁어냄
                                    clipRect(0f, top, ok, top + barH) {
                                        var x = -barH
                                        while (x < ok + barH) {
                                            drawLine(track.copy(alpha = 0.9f), Offset(x, top + barH), Offset(x + barH, top), strokeWidth = 3.dp.toPx(), pathEffect = hatch)
                                            x += 10.dp.toPx()
                                        }
                                    }
                                }
                                if (ov > 0f) drawRect(over, topLeft = Offset(ok, top), size = Size(ov, barH))
                                // 권장 주기 점선
                                val rx = px(recommended)
                                drawLine(colors.caution, Offset(rx, 0f), Offset(rx, size.height), strokeWidth = 2.dp.toPx(), pathEffect = dash)
                            }
                        },
                )
                Column(Modifier.width(150.dp)) {
                    Text(
                        b.valueText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (b.value > recommended) over else onSurface,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    b.overText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = over, maxLines = 1) }
                }
            }
        }
        Text(
            recommendedLabel, style = MaterialTheme.typography.bodySmall, color = colors.caution,
            modifier = Modifier.padding(start = 162.dp),
        )
    }
}
