package com.example.consumablelife.core.vehicle

import app.cash.turbine.test
import com.example.consumablelife.core.domain.WheelPos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** T4 FakeVehicleDataSource 시나리오 (Turbine) */
class FakeVehicleDataSourceTest {

    @Test fun `주행 증가 - 1km 버킷 단위로만 방출`() = runTest {
        val fake = FakeVehicleDataSource()
        fake.odometerKm().test {
            assertThat(awaitItem()).isEqualTo(FakeVehicleDataSource.INITIAL_ODO_KM)
            fake.addKm(0.4) // 같은 정수 km → 방출 없음 (FR-V05)
            expectNoEvents()
            fake.addKm(1.0)
            assertThat(awaitItem()).isWithin(1e-9).of(FakeVehicleDataSource.INITIAL_ODO_KM + 1.4)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `오일 레벨 저하 시나리오`() = runTest {
        val fake = FakeVehicleDataSource()
        fake.fluidAlerts().test {
            assertThat(awaitItem().engineOil.isLow).isFalse()
            fake.setScenario(FakeScenario.OIL_LOW)
            val a = awaitItem()
            assertThat(a.engineOil.isLow).isTrue()
            assertThat(a.washerLow).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `타이어 저압 시나리오 - 좌측 앞만 저압`() = runTest {
        val fake = FakeVehicleDataSource()
        fake.tireAlerts().test {
            assertThat(awaitItem().values.any { it }).isFalse()
            fake.setScenario(FakeScenario.TIRE_LOW)
            val m = awaitItem()
            assertThat(m.filterValues { it }.keys).containsExactly(WheelPos.FRONT_LEFT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `Odometer 미지원 - null 방출 + capability false`() = runTest {
        val fake = FakeVehicleDataSource()
        fake.setScenario(FakeScenario.ODOMETER_UNSUPPORTED)
        assertThat(fake.capabilities.value.odometer).isFalse()
        fake.odometerKm().test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `권한 미승인 - permissionDenied`() = runTest {
        val fake = FakeVehicleDataSource()
        fake.setScenario(FakeScenario.PERMISSION_DENIED)
        assertThat(fake.capabilities.value.permissionDenied).isTrue()
    }
}
