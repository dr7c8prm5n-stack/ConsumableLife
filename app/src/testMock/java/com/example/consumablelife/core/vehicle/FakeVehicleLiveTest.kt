package com.example.consumablelife.core.vehicle

import app.cash.turbine.test
import com.example.consumablelife.core.domain.WheelPos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** 품목 매칭 실측값(VehicleLive) — mock 시나리오와 파생 계산 */
class FakeVehicleLiveTest {

    @Test fun `정상 - 누적 트립·충전량·공기압이 들어오고 저압 없음`() = runTest {
        val fake = FakeVehicleDataSource()
        fake.liveData().test {
            val v = awaitItem()
            assertThat(v.tripCumulativeKm).isEqualTo(FakeVehicleDataSource.TRIP_BASE_KM)
            assertThat(v.evSocPercent).isEqualTo(78)
            assertThat(v.tirePressureKpa).hasSize(4)
            assertThat(v.lowTires.values.any { it }).isFalse()
            assertThat(v.averageSpeedKmh!!).isWithin(0.1).of(FakeVehicleDataSource.TRIP_AVG_KMH)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `타이어 저압 - 공기압이 기준보다 낮은 바퀴만 저압 + TPMS 경고등`() = runTest {
        val fake = FakeVehicleDataSource()
        fake.setScenario(FakeScenario.TIRE_LOW)
        fake.liveData().test {
            val v = awaitItem()
            assertThat(v.lowTires.filterValues { it }.keys).containsExactly(WheelPos.FRONT_LEFT)
            assertThat(v.telltaleOn(Telltale.TPMS)).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `출력 제한·12V 경고등 시나리오`() = runTest {
        val fake = FakeVehicleDataSource()
        fake.setScenario(FakeScenario.EV_POWER_DOWN)
        fake.liveData().test {
            assertThat(awaitItem().powerDownWarning).isTrue()
            fake.setScenario(FakeScenario.LOW_12V)
            val v = awaitItem()
            assertThat(v.powerDownWarning).isFalse()
            assertThat(v.telltaleOn(Telltale.LOW_VOLT_BATTERY)).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `권한 미승인 - 실측값 없음, API 상태는 권한 없음`() = runTest {
        val fake = FakeVehicleDataSource()
        fake.setScenario(FakeScenario.PERMISSION_DENIED)
        fake.liveData().test {
            val v = awaitItem()
            assertThat(v.evSocPercent).isNull()
            assertThat(v.telltales).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(fake.apiStatus.value.values.all { it.state == ApiState.PERMISSION_DENIED }).isTrue()
    }

    @Test fun `저압 판정은 현재 공기압과 기준값이 모두 있을 때만`() {
        val onlyThreshold = VehicleLive(tireWheels = setOf(WheelPos.FRONT_LEFT), tireLowThresholdKpa = mapOf(WheelPos.FRONT_LEFT to 200f))
        assertThat(onlyThreshold.lowTires[WheelPos.FRONT_LEFT]).isFalse()
        assertThat(VehicleLive(tripCumulativeKm = 100.0).averageSpeedKmh).isNull()
    }
}
