package com.example.consumablelife.app

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.consumablelife.core.designsystem.LocalMotion
import com.example.consumablelife.core.designsystem.MotionTokens
import com.example.consumablelife.core.vehicle.UxRestrictionState
import com.example.consumablelife.feature.consumables.ConsumablesScreen
import com.example.consumablelife.feature.consumables.SavedRecord
import com.example.consumablelife.feature.onboarding.OnboardingScreen
import com.example.consumablelife.feature.record.RecordScreen
import com.example.consumablelife.feature.record.SavedResult
import com.example.consumablelife.feature.settings.SettingsScreen

val LocalUx = staticCompositionLocalOf { UxRestrictionState.Unrestricted }

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val RECORD = "record/{id}"
    const val SETTINGS = "settings"

    fun record(id: String) = "record/$id"

    /** 기록 저장 결과를 탭 화면에 돌려주는 savedStateHandle 키 */
    const val SAVED = "savedRecord"
}

/** UX-15 최대 탐색 깊이 2단계: 탭 화면(차량·대시보드·타임라인) → 상세 시트 / 기록 / 설정 */
@Composable
fun AppNavHost(startRoute: String, activity: Activity) {
    val nav = rememberNavController()
    val motion = LocalMotion.current
    val slide = with(LocalDensity.current) { 24.dp.roundToPx() }

    // FR-L03: 최상위 화면에서 Back → moveTaskToBack(true). finish() 금지
    val entry by nav.currentBackStackEntryAsState()
    BackHandler(enabled = entry != null && nav.previousBackStackEntry == null) {
        activity.moveTaskToBack(true)
    }

    // AN-06 화면 전환: fade + 수평 슬라이드 24dp, 250ms (MotionPolicy NONE → 전환 없음)
    val enter: EnterTransition = if (motion.full) {
        fadeIn(tween(MotionTokens.NAV_MS)) + slideInHorizontally(tween(MotionTokens.NAV_MS)) { slide }
    } else EnterTransition.None
    val exit: ExitTransition = if (motion.full) fadeOut(tween(MotionTokens.NAV_MS)) else ExitTransition.None
    val popEnter: EnterTransition = if (motion.full) {
        fadeIn(tween(MotionTokens.NAV_MS)) + slideInHorizontally(tween(MotionTokens.NAV_MS)) { -slide }
    } else EnterTransition.None
    val popExit: ExitTransition = if (motion.full) {
        fadeOut(tween(MotionTokens.NAV_MS)) + slideOutHorizontally(tween(MotionTokens.NAV_MS)) { slide }
    } else ExitTransition.None

    NavHost(
        navController = nav, startDestination = startRoute,
        enterTransition = { enter }, exitTransition = { exit },
        popEnterTransition = { popEnter }, popExitTransition = { popExit },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.DASHBOARD) { entry ->
            // 기록 화면이 저장 결과를 "id|recordId|from|status|cycle" 문자열로 돌려준다 → 탭 화면이 리필·실행 취소 처리
            val savedRaw by entry.savedStateHandle.getStateFlow<String?>(Routes.SAVED, null).collectAsStateWithLifecycle()
            ConsumablesScreen(
                saved = savedRaw?.let(::decodeSaved),
                onSavedConsumed = { entry.savedStateHandle[Routes.SAVED] = null },
                onRecord = { nav.navigate(Routes.record(it)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.RECORD, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            RecordScreen(
                onBack = { nav.popBackStack() },
                onSaved = { id, result -> nav.afterRecordSaved(id, result) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onResetDone = {
                    nav.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
                },
            )
        }
    }
}

/** FR-R04: 저장 성공 → 탭 화면으로 돌아가 상세 시트에서 리필(DG2-AN-12 / AN-07) 재생 */
private fun NavHostController.afterRecordSaved(id: String, r: SavedResult) {
    previousBackStackEntry?.savedStateHandle?.set(
        Routes.SAVED, listOf(id, r.recordId, r.fromProgress, r.fromStatus, r.fromCycleIndex).joinToString("|"),
    )
    popBackStack()
}

private fun decodeSaved(raw: String): SavedRecord? {
    val p = raw.split('|')
    if (p.size != 5) return null
    return SavedRecord(
        itemId = p[0], recordId = p[1].toLongOrNull() ?: return null,
        fromProgress = p[2].toFloatOrNull() ?: 0f, fromStatus = p[3], fromCycleIndex = p[4].toIntOrNull() ?: 1,
    )
}
