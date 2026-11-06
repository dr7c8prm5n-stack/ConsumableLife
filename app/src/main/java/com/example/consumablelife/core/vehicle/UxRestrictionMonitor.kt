package com.example.consumablelife.core.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

data class UxRestrictionState(
    val requiresDistractionOptimization: Boolean,
    val noKeyboard: Boolean,   // UX_RESTRICTIONS_NO_KEYBOARD
    val noSetup: Boolean,      // UX_RESTRICTIONS_NO_SETUP
    val limitContent: Boolean, // UX_RESTRICTIONS_LIMIT_CONTENT
) {
    companion object {
        val Unrestricted = UxRestrictionState(false, false, false, false)
        val FullyRestricted = UxRestrictionState(true, true, true, true)
    }
}

/**
 * 12.1 CarUxRestrictionsManager 래핑. MVP는 DO 미선언이라 주행 중 시스템이 앱을 차단하지만,
 * MotionPolicy와 입력 화면(S3 메모, S4 설정) 활성 조건으로 사용한다 (FR-D01).
 * AC-P 테스트 훅: [forceRestricted] (설정 > 개발자 옵션).
 */
@Singleton
class UxRestrictionMonitor @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val forceRestricted = MutableStateFlow(false)

    private val isAutomotive = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    private val carState = callbackFlow {
        if (!isAutomotive) {
            trySend(UxRestrictionState.Unrestricted)
            awaitClose { }
            return@callbackFlow
        }
        val car = runCatching { Car.createCar(ctx) }.getOrNull()
        val mgr = runCatching {
            car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        }.getOrNull()
        if (mgr == null) {
            Log.w("ConsumableLife", "CarUxRestrictionsManager unavailable → Unrestricted")
            trySend(UxRestrictionState.Unrestricted)
            awaitClose { car?.disconnect() }
            return@callbackFlow
        }
        trySend(mgr.currentCarUxRestrictions.toState())
        mgr.registerListener { r -> trySend(r.toState()) }
        awaitClose {
            runCatching { mgr.unregisterListener() }
            runCatching { car?.disconnect() }
        }
    }

    val state: StateFlow<UxRestrictionState> = combine(carState, forceRestricted) { s, force ->
        if (force) UxRestrictionState.FullyRestricted else s
    }.stateIn(appScope, SharingStarted.WhileSubscribed(5_000), UxRestrictionState.Unrestricted)

    private fun CarUxRestrictions?.toState(): UxRestrictionState {
        if (this == null) return UxRestrictionState.Unrestricted
        val active = activeRestrictions
        return UxRestrictionState(
            requiresDistractionOptimization = isRequiresDistractionOptimization,
            noKeyboard = active and CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD != 0,
            noSetup = active and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP != 0,
            limitContent = active and CarUxRestrictions.UX_RESTRICTIONS_LIMIT_CONTENT != 0,
        )
    }
}
