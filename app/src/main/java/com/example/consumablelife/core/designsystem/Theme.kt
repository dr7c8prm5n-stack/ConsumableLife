package com.example.consumablelife.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.consumablelife.core.domain.Status

/*
 * 설계서 v3 5장 디자인 토큰 — Pleos 기본 화면 톤(순흑 / 라이트 #F2F3F5 베이스).
 *  TK-01 유채색은 상태색(링·바·칩·마커·뱃지)과 accent(주 버튼·선택 테두리·탭 뱃지)뿐. 나머지 면·선·글자는 무채 토큰.
 *  TK-02 상태 틴트 배경(*Bg)은 상태색 12~16% 알파, 틴트 위 글자는 상태색 원색.
 *  UX-10 상태색은 카드 면 대비 4.5:1 (DesignPolicyTest) — 라이트 상태색 3종은 설계서 값보다 어둡게 보정.
 */
@Immutable
data class StatusColors(
    val good: Color, val caution: Color, val due: Color, val overdue: Color, val unknown: Color,
    /** 상태색 배경 위 텍스트/아이콘 */
    val onStatus: Color,
) {
    fun of(status: Status): Color = when (status) {
        Status.GOOD -> good
        Status.CAUTION -> caution
        Status.DUE -> due
        Status.OVERDUE, Status.SENSOR_ALERT -> overdue
        Status.UNKNOWN -> unknown
    }
}

/** 설계서 v3 5장 ConsumableColors — Compose에서는 [LocalAppColors]로 제공 */
@Immutable
data class AppColors(
    val bg: Color, val bgApp: Color, val surface: Color, val surface2: Color, val surface3: Color,
    val line: Color, val line2: Color, val text: Color, val text2: Color, val text3: Color,
    val accent: Color, val onAccent: Color,
    val status: StatusColors,
    /** 상태 틴트 알파 (good / caution / due·overdue) */
    val goodBgAlpha: Float, val cautionBgAlpha: Float, val dueBgAlpha: Float,
    val track: Color,
    /** X-ray 선 색 */
    val carInner: Color,
    val scrim: Color,
    val dark: Boolean,
) {
    fun statusColor(s: Status): Color = status.of(s)

    /** 칩·아이콘 틴트 배경 */
    fun statusTint(s: Status): Color = statusColor(s).copy(
        alpha = when (s) {
            Status.GOOD -> goodBgAlpha
            Status.CAUTION -> cautionBgAlpha
            Status.UNKNOWN -> cautionBgAlpha
            else -> dueBgAlpha
        },
    )
}

object Tokens {
    val NightStatus = StatusColors(
        good = Color(0xFF3DCB82), caution = Color(0xFFF2B13E), due = Color(0xFFFF6A50),
        overdue = Color(0xFFFF5C66), unknown = Color(0xFF9AA5B4), onStatus = Color(0xFF0A0B0E),
    )
    /** 설계서 라이트 값(#1E9E5A / #BF8300 / #DF4A2E)은 흰 면 대비 4.5:1 미달 → 같은 색상에서 어둡게 보정 */
    val DayStatus = StatusColors(
        good = Color(0xFF117A4B), caution = Color(0xFF8F5600), due = Color(0xFFB8401F),
        overdue = Color(0xFFC9202B), unknown = Color(0xFF5F6673), onStatus = Color(0xFFFFFFFF),
    )

    val Night = AppColors(
        bg = Color(0xFF000000), bgApp = Color(0xFF0A0B0E), surface = Color(0xFF15171B), surface2 = Color(0xFF1F2227),
        surface3 = Color(0xFF2A2E35), line = Color(0xFF272A31), line2 = Color(0xFF363A43),
        text = Color(0xFFF5F6F8), text2 = Color(0xFFA4A8B1), text3 = Color(0xFF8A8F99),
        accent = Color(0xFF3D7BFF), onAccent = Color(0xFFFFFFFF), status = NightStatus,
        goodBgAlpha = 0.14f, cautionBgAlpha = 0.16f, dueBgAlpha = 0.16f,
        track = Color(0xFF2A2E35), carInner = Color(0xFF55C98A), scrim = Color(0x66000000), dark = true,
    )
    val Day = AppColors(
        bg = Color(0xFFF2F3F5), bgApp = Color(0xFFF7F8FA), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF0F1F4),
        surface3 = Color(0xFFE4E6EB), line = Color(0xFFE3E5EA), line2 = Color(0xFFD2D5DC),
        text = Color(0xFF15171C), text2 = Color(0xFF5F636C), text3 = Color(0xFF6B6F78),
        accent = Color(0xFF2E6BE6), onAccent = Color(0xFFFFFFFF), status = DayStatus,
        goodBgAlpha = 0.12f, cautionBgAlpha = 0.14f, dueBgAlpha = 0.12f,
        track = Color(0xFFE3E5EA), carInner = Color(0xFF1E9E5A), scrim = Color(0x3D000000), dark = false,
    )

    val DaySurface = Day.surface
    val DayOnSurface = Day.text
    val DayBackground = Day.bgApp
    val NightSurface = Night.surface
    val NightOnSurface = Night.text
    val NightBackground = Night.bgApp
    val Accent = Day.accent
    val AccentNight = Night.accent

    /** 앱 영역 배경 — 그라데이션 없이 한 면 (TK-04). 도식은 배경을 칠하지 않으므로 이 색이 차량 주변에 그대로 보인다 */
    val NightBackdrop = listOf(Night.bgApp, Night.bgApp, Night.bg)
    val DayBackdrop = listOf(Day.bgApp, Day.bgApp, Day.bg)

    // UX-12 텍스트 sp 하한 (실차 dpi별 4mm/3mm 실측 VERIFY)
    val CoreSp = 32.sp
    val BodySp = 20.sp
    val AuxSp = 16.sp

    // UX-13 터치 타깃 76dp, 간격 16dp (Pleos 버튼 규격 VERIFY)
    val TouchTarget = 76.dp
    val TouchGap = 16.dp
    val Gutter = 32.dp
    // 설계서 v3 4장: 카드 24, 버튼 20, 세그먼트 16
    val CardRadius = 24.dp
    val ButtonRadius = 20.dp
    val ControlRadius = 16.dp
    /** 헤더 좌측 세로 강조선 두께 */
    val AccentBar = 4.dp
}

val LocalAppColors = staticCompositionLocalOf { Tokens.Day }
val LocalStatusColors = staticCompositionLocalOf { Tokens.DayStatus }

/** 앱 루트 배경 (bgApp 단색) */
fun Modifier.appBackdrop(dark: Boolean): Modifier {
    val c = if (dark) Tokens.Night.bgApp else Tokens.Day.bgApp
    return drawBehind { drawRect(c) }
}

/** 카드 표면: surface 단색 (v3 — 유리 효과 없음, 1dp line 테두리로 구분) */
@Composable
fun glassSurface(): Color = MaterialTheme.colorScheme.surface

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold, lineHeight = 64.sp, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp, letterSpacing = (-0.9).sp),
    headlineLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 48.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontSize = Tokens.CoreSp, fontWeight = FontWeight.Bold, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    bodyLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontSize = 22.sp, lineHeight = 30.sp),
    bodySmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    labelMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    labelSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(Tokens.ControlRadius),
    medium = RoundedCornerShape(Tokens.ButtonRadius),
    large = RoundedCornerShape(Tokens.CardRadius),
    extraLarge = RoundedCornerShape(28.dp),
)

/** UX-11 / DN-01: 시스템 다크 모드 연동, 앱 내 강제 토글 없음. 테마 전환은 토큰 교체만으로 (DN-02) */
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) Tokens.Night else Tokens.Day
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            primaryContainer = c.surface3, onPrimaryContainer = c.text,
            secondary = c.accent, onSecondary = c.onAccent,
            secondaryContainer = c.surface3, onSecondaryContainer = c.text,
            tertiaryContainer = c.surface2, onTertiaryContainer = c.text,
            background = c.bgApp, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surface2, onSurfaceVariant = c.text2,
            surfaceContainerLowest = c.bgApp, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface3,
            outline = c.line2, outlineVariant = c.line, scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            primaryContainer = c.surface3, onPrimaryContainer = c.text,
            secondary = c.accent, onSecondary = c.onAccent,
            secondaryContainer = c.surface3, onSecondaryContainer = c.text,
            tertiaryContainer = c.surface2, onTertiaryContainer = c.text,
            background = c.bgApp, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surface2, onSurfaceVariant = c.text2,
            surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface3,
            outline = c.line2, outlineVariant = c.line, scrim = Color.Black,
        )
    }
    CompositionLocalProvider(LocalAppColors provides c, LocalStatusColors provides c.status) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
