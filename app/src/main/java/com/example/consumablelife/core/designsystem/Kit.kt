package com.example.consumablelife.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.consumablelife.core.domain.Status

/*
 * 설계서 v3 4·6장 — 3탭 화면의 치수·타이포·공통 컴포넌트.
 * 설계서의 목업은 앱 영역 1060 × 756 px로 그려졌고 실제 창(≈1657 × 1170 dp)에서는 ×1.5625다.
 * 고정 px를 쓰지 않도록(RS-01) 모든 치수는 "목업 px × k"로 계산한다. k = 1.5625 × (창 크기 배율).
 */
@Immutable
class Ui(val scale: Float) {
    private val k = BASE * scale

    /** 목업 px → dp */
    fun dp(px: Number): Dp = (px.toFloat() * k).dp

    /** 4장 타이포 표의 sp(기준 창) → 현재 창. UX-12 하한을 지킨다 */
    fun sp(base: Number, min: Float = 16f): TextUnit = maxOf(base.toFloat() * scale, min).sp

    private fun style(size: Number, weight: FontWeight, min: Float = 16f, tracking: Float = 0f) = TextStyle(
        fontSize = sp(size, min), fontWeight = weight, lineHeight = sp(size.toFloat() * 1.28f, min * 1.28f), letterSpacing = tracking.sp,
    )

    /** 주행거리 48sp */
    val display get() = style(48, FontWeight.Bold, 32f, -0.9f)
    /** 히어로 항목명 40sp */
    val headline get() = style(40, FontWeight.Bold, 28f, -0.6f)
    /** 핵심 수치 34sp (UX-12 핵심 32sp+) */
    val number get() = style(34, FontWeight.Bold, 26f, -0.5f)
    /** 상세 제목 28~30sp */
    val title get() = style(28, FontWeight.Bold, 22f, -0.3f)
    val bodyL get() = style(26, FontWeight.Normal, 20f)
    val body get() = style(24, FontWeight.SemiBold, 20f)
    val label get() = style(22, FontWeight.Normal, 18f)
    val labelBold get() = style(22, FontWeight.SemiBold, 18f)
    val labelS get() = style(20, FontWeight.SemiBold, 16f)
    val caption get() = style(19, FontWeight.Normal, 16f)

    val cardShape get() = RoundedCornerShape(dp(16))
    val gap get() = dp(12)

    companion object {
        const val BASE = 1.5625f
        /** 설계서 기준 창 (Connect-S 1창 앱 영역) */
        const val REF_W = 1657f
        const val REF_H = 1170f

        /** 창 크기에 맞춘 배율 — 세로 스크롤 없이 한 화면에 들어가도록 가로·세로 중 작은 쪽 기준 */
        fun forWindow(widthDp: Float, heightDp: Float): Ui =
            Ui(minOf(widthDp / REF_W, heightDp / REF_H).coerceIn(0.55f, 1.25f))
    }
}

val LocalUi = staticCompositionLocalOf { Ui(1f) }

/** AN-04 링·바 값 보간: 600ms EaseOutCubic */
val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
/** 화면 전환·확대에 쓰는 부드러운 감속 곡선 */
val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
/** 도식 스캔처럼 양끝이 부드러운 이동 */
val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

/** 카드: surface 면 + 1dp line 테두리. 그림자는 차량 탭 플로팅 상세 카드에만 (TK-03) */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    borderColor: Color = LocalAppColors.current.line,
    borderWidth: Dp = 1.dp,
    elevated: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val app = LocalAppColors.current
    val shape = LocalUi.current.cardShape
    Box(
        modifier
            .then(
                if (elevated) Modifier.shadow(
                    if (app.dark) 32.dp else 28.dp, shape,
                    ambientColor = Color.Black.copy(alpha = if (app.dark) 0.55f else 0.14f),
                    spotColor = Color.Black.copy(alpha = if (app.dark) 0.55f else 0.14f),
                ) else Modifier,
            )
            .clip(shape)
            .background(app.surface)
            .border(borderWidth, borderColor, shape),
        content = content,
    )
}

@Composable
fun Dot(color: Color, size: Dp = LocalUi.current.dp(9), modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color, CircleShape))
}

/**
 * LifeRing (6장): 두께 = 크기 × 0.11, 트랙 = track, 진행 = 상태색, 12시 시작 시계방향, 끝 round.
 * 중앙 글자 = 크기 × 0.24. 값이 바뀌면 600ms EaseOutCubic으로 보간 (AN-04, MotionPolicy NONE이면 즉시).
 */
@Composable
fun Ring(
    fraction: Float?,
    color: Color,
    size: Dp,
    label: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    appear: () -> Float = { 1f },
) {
    val app = LocalAppColors.current
    val v by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), motionSpec(tween(600, easing = EaseOutCubic)), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val sw = this.size.minDimension * 0.11f
            val arc = Size(this.size.width - sw, this.size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(app.track, -90f, 360f, false, tl, arc, style = Stroke(sw))
            val sweep = 360f * v * appear().coerceIn(0f, 1f)
            if (sweep > 0.5f) drawArc(color, -90f, sweep, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label, color = app.text, fontWeight = FontWeight.Bold, maxLines = 1,
                fontSize = maxOf(size.value * 0.24f, 14f).sp, letterSpacing = (-0.5).sp,
            )
            if (sub != null) Text(sub, color = app.text3, fontSize = maxOf(size.value * 0.11f, 14f).sp, maxLines = 1)
        }
    }
}

/** LifeBar (6장): 트랙 track + 상태색 채움. 값 변경 시 600ms 보간 */
@Composable
fun LifeBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = LocalUi.current.dp(6)) {
    val track = LocalAppColors.current.track
    val v by animateFloatAsState(fraction.coerceIn(0f, 1f), motionSpec(tween(600, easing = EaseOutCubic)), label = "bar")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (v > 0f) drawRoundRect(color, size = Size(maxOf(size.width * v, size.height), size.height), cornerRadius = r)
    }
}

/** StatusChip (6장): pill, 틴트 배경 + 상태색 아이콘·글자. 색만으로 구분하지 않도록 아이콘 필수 */
@Composable
fun StatusPill(status: Status, label: String, modifier: Modifier = Modifier, small: Boolean = false) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val fg = app.statusColor(status)
    Row(
        modifier
            .height(ui.dp(if (small) 22 else 26))
            .clip(RoundedCornerShape(50))
            .background(app.statusTint(status))
            .padding(horizontal = ui.dp(if (small) 8 else 10))
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ui.dp(5)),
    ) {
        Icon(StatusIcons.of(status), null, tint = fg, modifier = Modifier.size(ui.dp(14)))
        Text(label, color = fg, style = if (small) ui.caption.copy(fontWeight = FontWeight.SemiBold) else ui.labelS, maxLines = 1)
    }
}

/** ItemIcon (6장): 56dp 둥근 사각형. due/caution은 상태 틴트 + 상태색, good은 무채 */
@Composable
fun ItemGlyph(iconKey: String, status: Status, modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val tinted = status != Status.GOOD && status != Status.UNKNOWN
    Box(
        modifier.size(ui.dp(36)).background(if (tinted) app.statusTint(status) else app.surface2, RoundedCornerShape(ui.dp(10))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ItemIcons.of(iconKey), null, tint = if (tinted) app.statusColor(status) else app.text2, modifier = Modifier.size(ui.dp(19)))
    }
}

@Immutable
data class Seg(val label: String, val dot: Color? = null, val icon: ImageVector? = null, val badge: String? = null)

/**
 * SegmentedControl / TabBar (6장): 배경 surface2, 활성 항목 = surface 면 + text. 유채색 없음 (TK-01).
 * @param tall 탭바(항목 높이 68dp) / 일반 세그먼트(56dp)
 */
@Composable
fun Segmented(
    items: List<Seg>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tall: Boolean = false,
) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val outer = RoundedCornerShape(ui.dp(if (tall) 13 else 11))
    val inner = RoundedCornerShape(ui.dp(if (tall) 10 else 9))
    Row(
        modifier.background(app.surface2, outer).padding(ui.dp(if (tall) 4 else 3)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, s ->
            val on = i == selected
            Row(
                Modifier
                    .height(ui.dp(if (tall) 44 else 34))
                    .clip(inner)
                    .then(if (on) Modifier.background(app.surface).then(if (!app.dark) Modifier.border(1.dp, app.line, inner) else Modifier) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .semantics { this.selected = on }
                    .padding(horizontal = ui.dp(if (tall) 20 else 14)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ui.dp(if (tall) 8 else 6)),
            ) {
                if (s.dot != null) Dot(s.dot)
                if (s.icon != null) Icon(s.icon, null, tint = if (on) app.text else app.text2, modifier = Modifier.size(ui.dp(18)))
                Text(
                    s.label, color = if (on) app.text else app.text2, maxLines = 1,
                    style = (if (tall) ui.body else ui.label).copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                )
                if (s.badge != null) {
                    // NV-05 조치 필요 건수 뱃지 (due 색)
                    Text(
                        s.badge, color = app.status.due, style = ui.caption.copy(fontWeight = FontWeight.Bold), maxLines = 1,
                        modifier = Modifier.background(app.statusTint(Status.DUE), RoundedCornerShape(50)).padding(horizontal = ui.dp(7), vertical = ui.dp(1)),
                    )
                }
            }
        }
    }
}

/** PrimaryButton (6장): accent 면 — 화면당 1개. SecondaryButton: surface + 1dp line2 */
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    small: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val shape = RoundedCornerShape(ui.dp(if (small) 11 else 13))
    val fg = if (primary) app.onAccent else app.text
    Row(
        modifier
            .defaultMinSize(minHeight = ui.dp(if (small) 44 else 52))
            .clip(shape)
            .background(if (primary) app.accent.copy(alpha = if (enabled) 1f else 0.4f) else app.surface)
            .then(if (primary) Modifier else Modifier.border(1.dp, app.line2, shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = ui.dp(12)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(ui.dp(20)))
            Spacer(Modifier.width(ui.dp(8)))
        }
        Text(
            text, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = if (small) ui.labelBold else ui.body,
        )
    }
}

/** 아이콘 버튼 (64dp): surface2 면. 켜짐 상태는 accent 테두리 */
@Composable
fun IconTile(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    val shape = RoundedCornerShape(ui.dp(11))
    Box(
        modifier
            .size(ui.dp(40))
            .clip(shape)
            .background(app.surface2)
            .then(if (active) Modifier.border(2.dp, app.accent, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description; selected = active },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) app.text else app.text2, modifier = Modifier.size(ui.dp(20)))
    }
}

/** KeyValueRow (6장): 좌 라벨 text3, 우 값 text 우정렬, 하단 1dp line */
@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, divider: Boolean = true, valueColor: Color? = null) {
    val app = LocalAppColors.current
    val ui = LocalUi.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = ui.dp(8)),
            horizontalArrangement = Arrangement.spacedBy(ui.dp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = app.text3, style = ui.label, maxLines = 1)
            Text(
                value, color = valueColor ?: app.text, style = ui.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End, modifier = Modifier.weight(1f),
            )
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(app.line))
    }
}
