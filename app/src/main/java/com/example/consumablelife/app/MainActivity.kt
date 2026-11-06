package com.example.consumablelife.app

import android.app.Application
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.consumablelife.core.data.ConsumableRepository
import com.example.consumablelife.core.data.TodayProvider
import com.example.consumablelife.core.designsystem.AppTheme
import com.example.consumablelife.core.designsystem.LocalMotion
import com.example.consumablelife.core.designsystem.appBackdrop
import com.example.consumablelife.core.designsystem.motionOf
import com.example.consumablelife.core.vehicle.UxRestrictionMonitor
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ConsumableLifeApp : Application()

@HiltViewModel
class MainViewModel @Inject constructor(repo: ConsumableRepository) : ViewModel() {
    /** null = 로딩 중(DataStore 첫 읽기, 수 ms) */
    val startRoute: StateFlow<String?> = repo.settings
        .map { if (it.onboardingDone) Routes.DASHBOARD else Routes.ONBOARDING }
        .take(1) // 시작 화면은 1회만 결정 (이후 전환은 NavController가 담당)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var uxMonitor: UxRestrictionMonitor
    @Inject lateinit var repository: ConsumableRepository
    @Inject lateinit var todayProvider: TodayProvider

    private val animScale = mutableFloatStateOf(1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 명세 v2.0 0장 "대체": 프리셋 프로필(아이오닉 5)이 바뀌었으면 품목을 교체 (기록·사용자 항목 유지)
        lifecycleScope.launch { repository.syncProfile() }
        // FR-V05: 주행 스냅샷 기록은 foreground(STARTED) 동안만 — 백그라운드 센서 구독 금지 (FR-V03)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { repository.trackOdometer() }
        }

        setContent {
            val vm: MainViewModel = viewModel()
            val start by vm.startRoute.collectAsStateWithLifecycle()
            val ux by uxMonitor.state.collectAsStateWithLifecycle()
            val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
            val motion = motionOf(ux, animScale.floatValue, lifecycleState.isAtLeast(Lifecycle.State.RESUMED))

            AppTheme {
                CompositionLocalProvider(LocalMotion provides motion, LocalUx provides ux) {
                    // Pleos 홈 톤의 배경 그라데이션 — 투명 도식(DG2-01)이 이 위에 그대로 놓인다
                    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    Box(Modifier.fillMaxSize().appBackdrop(dark)) {
                        start?.let { AppNavHost(startRoute = it, activity = this@MainActivity) }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // FR-L01: foreground 복귀 시 오늘 날짜로 재계산 (애니메이션은 재생하지 않음 — PlayedAnimations)
        todayProvider.refresh()
        // AN-R3: 시스템 애니메이션 배율
        animScale.floatValue = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
}
