package com.example.consumablelife.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.consumablelife.core.designsystem.diagram.rememberOneShotTimeline
import com.example.consumablelife.core.domain.Status
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * AN-01 타임라인: 화면 최초 표시 시 0 → timelineMs(ms)로 선형 진행. 화면당 1회(PlayedAnimations).
 * MotionPolicy NONE(DO·애니메이션 배율 0·백그라운드)이면 최종값으로 스냅 (AN-R2, AN-R3).
 */
@Composable
fun rememberSweepTimeline(key: String, count: Int): State<Float> =
    rememberOneShotTimeline(key, MotionTokens.timelineMs(count))

/**
 * 원형 수명 게이지. 값은 람다로 받아 draw 단계에서만 읽음 (AN-R4: recomposition 최소화).
 * @param fraction AN-01 스윕 진행률 0..1
 * @param percent AN-03 값 전환이 적용된 현재 퍼센트 0..100
 */
@Composable
fun LifeRing(
    percent: () -> Float,
    color: () -> Color,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    stroke: Dp = 9.dp,
    fraction: () -> Float = { 1f },
    content: @Composable () -> Unit = {},
) {
    val track = LocalAppColors.current.track
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            val sweep = 360f * (percent() / 100f).coerceIn(0f, 1f) * fraction().coerceIn(0f, 1f)
            if (sweep > 0.5f) {
                drawArc(color(), -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** AN-03 값 전환: 400ms tween / 색 300ms */
@Composable
fun animatedPercent(target: Float): State<Float> =
    animateFloatAsState(target, motionSpec(tween(MotionTokens.VALUE_MS, easing = MotionTokens.Sweep)), label = "percent")

@Composable
fun animatedStatusColor(status: Status): State<Color> =
    animateColorAsState(LocalStatusColors.current.of(status), motionSpec(tween(MotionTokens.COLOR_MS)), label = "statusColor")

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()

fun formatNumber(v: Int): String = numberFormat.format(v)

/** AN-02 숫자 카운트업 — 게이지와 같은 진행률 사용, 정수 반올림·천 단위 콤마 */
@Composable
fun CountUpText(
    value: Float,
    progress: Float,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = Color.Unspecified,
    suffix: String = "",
) {
    val shown = (value * progress.coerceIn(0f, 1f)).roundToInt()
    Text(formatNumber(shown) + suffix, modifier, style = style, color = color, maxLines = 1)
}

/**
 * UX-04 / 설계서 v3 6장 StatusChip: 상태는 색 + 아이콘 + 텍스트 병기. 틴트 배경(상태색 12~16%) 위에 상태색 원색 글자 (TK-02).
 * AN-03 칩 텍스트 교체는 AnimatedContent.
 */
@Composable
fun StatusChip(status: Status, label: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val app = LocalAppColors.current
    val fg by animatedStatusColor(status)
    val motion = LocalMotion.current
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(app.statusTint(status))
            .padding(horizontal = if (large) 16.dp else 12.dp, vertical = if (large) 8.dp else 5.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(StatusIcons.of(status), null, tint = fg, modifier = Modifier.size(if (large) 24.dp else 20.dp))
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                if (motion.full) fadeIn(tween(MotionTokens.COLOR_MS)) togetherWith fadeOut(tween(MotionTokens.COLOR_MS))
                else fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            },
            label = "chip",
        ) { text ->
            Text(
                text, color = fg, maxLines = 1,
                style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * 상세 화면 거리/기간 바. 제한 요인 쪽 바를 강조 (S2).
 * @param remaining 남은 비율 0..1
 */
@Composable
fun LinearLifeBar(
    remaining: () -> Float,
    color: () -> Color,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = LocalAppColors.current.track
    val h = if (emphasized) 12.dp else 10.dp
    Canvas(modifier.fillMaxWidth().height(h)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * remaining().coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(
                color().copy(alpha = if (emphasized) 1f else 0.6f),
                size = Size(maxOf(w, size.height), size.height), cornerRadius = r,
            )
        }
    }
}

/**
 * 화면 제목: 좌측 accent 세로 강조선 + 작은 캡션 라벨 + 큰 제목.
 * @param caption 작은 캡션(자간 넓은 labelSmall), 없으면 생략
 */
@Composable
fun BrandTitle(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.headlineLarge,
) {
    val accent = LocalAppColors.current.accent
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(Tokens.AccentBar)
                .height(if (caption != null) 56.dp else 36.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            // 색을 명시: Surface 밖(온보딩 루트 등)에서도 배경 대비가 보장되도록
            Text(title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 아이콘 배지: 연한 주색 배경의 둥근 사각형 안에 주색 아이콘 — 항목 아이콘을 한 가지 톤으로 정렬 */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier
            .size(size)
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(size / 4)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.6f))
    }
}
