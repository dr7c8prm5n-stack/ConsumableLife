package com.example.consumablelife.core.data

import com.example.consumablelife.core.domain.CarZone
import com.example.consumablelife.core.domain.Condition
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.Interval
import com.example.consumablelife.core.domain.Powertrain
import com.example.consumablelife.core.domain.SensorLink
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/** 명세 v2.0 3장 — 아이오닉 5 품목·주기 프리셋 (좌표 JSON과 같은 파일) */
class PresetLoaderTest {
    private val preset = PresetLoader.parse(File("src/main/assets/diagram/ioniq5_lifecycle_hotspots.json").readText())
    private val items = preset.items

    @Test fun `프로필 IONIQ5_LIFECYCLE, EV 기준 10개 품목`() {
        assertThat(preset.profileId).isEqualTo("IONIQ5_LIFECYCLE")
        assertThat(items.map { it.id }).containsExactly(
            "BATTERY_12V", "WIPER", "CABIN_FILTER", "HV_BATTERY", "TIRE_ROTATION",
            "COOLANT", "BRAKE_PAD", "BRAKE_FLUID", "REDUCER_OIL", "TIRE_REPLACE",
        ).inOrder()
        assertThat(items.all { it.appliesTo(Powertrain.EV) }).isTrue()
        assertThat(items.first { it.id == "HV_BATTERY" }.appliesTo(Powertrain.ICE)).isFalse()
    }

    @Test fun `주기 종류 - USER 기본값 배지, INSPECT 점검, REPLACE 교체`() {
        val bat = items.first { it.id == "BATTERY_12V" }
        assertThat(bat.cycleKind).isEqualTo(CycleKind.USER)
        assertThat(bat.isUserDefault).isTrue()
        assertThat(bat.normal).isEqualTo(Interval(null, 48))
        assertThat(bat.needsConfirm).isTrue() // verify
        val hv = items.first { it.id == "HV_BATTERY" }
        assertThat(hv.cycleKind).isEqualTo(CycleKind.INSPECT)
        assertThat(hv.normal).isEqualTo(Interval(null, 12))
        assertThat(hv.zone).isEqualTo(CarZone.UNDERBODY)
        val cabin = items.first { it.id == "CABIN_FILTER" }
        assertThat(cabin.cycleKind).isEqualTo(CycleKind.REPLACE)
        assertThat(cabin.isUserDefault).isFalse()
        assertThat(cabin.normal).isEqualTo(Interval(15_000, null))
        assertThat(cabin.needsConfirm).isFalse()
    }

    @Test fun `DG2-I3 냉각수 최초 주기 200,000km 120개월 → 이후 40,000km 24개월`() {
        val c = items.first { it.id == "COOLANT" }
        assertThat(c.first).isEqualTo(Interval(200_000, 120))
        assertThat(c.activeInterval(Condition.NORMAL, 0)).isEqualTo(Interval(200_000, 120))
        assertThat(c.activeInterval(Condition.NORMAL, 1)).isEqualTo(Interval(40_000, 24))
    }

    @Test fun `감속기 오일 - 통상 INSPECT 60,000km, 가혹 REPLACE 120,000km`() {
        val r = items.first { it.id == "REDUCER_OIL" }
        assertThat(r.cycleKind).isEqualTo(CycleKind.INSPECT)
        assertThat(r.activeKind(Condition.NORMAL)).isEqualTo(CycleKind.INSPECT)
        assertThat(r.activeKind(Condition.SEVERE)).isEqualTo(CycleKind.REPLACE)
        assertThat(r.activeInterval(Condition.NORMAL, 1)).isEqualTo(Interval(60_000, null))
        assertThat(r.activeInterval(Condition.SEVERE, 1)).isEqualTo(Interval(120_000, null))
    }

    @Test fun `센서 연동·구역 매핑`() {
        assertThat(items.first { it.id == "TIRE_ROTATION" }.sensorLink).isEqualTo(SensorLink.TIRE)
        assertThat(items.first { it.id == "TIRE_ROTATION" }.zone).isEqualTo(CarZone.WHEELS)
        assertThat(items.first { it.id == "BRAKE_FLUID" }.sensorLink).isEqualTo(SensorLink.BRAKE_FLUID_LEVEL)
        assertThat(items.first { it.id == "WIPER" }.sensorLink).isEqualTo(SensorLink.WASHER)
        // presetRev 2: 품목 ↔ Pleos Vehicle API 연결 추가
        assertThat(items.first { it.id == "BATTERY_12V" }.sensorLink).isEqualTo(SensorLink.LOW_VOLT_BATTERY)
        assertThat(items.first { it.id == "HV_BATTERY" }.sensorLink).isEqualTo(SensorLink.EV_POWER)
        assertThat(items.first { it.id == "REDUCER_OIL" }.sensorLink).isEqualTo(SensorLink.ATF_LEVEL)
        assertThat(items.first { it.id == "TIRE_REPLACE" }.sensorLink).isEqualTo(SensorLink.TIRE)
        assertThat(items.first { it.id == "WIPER" }.zone).isEqualTo(CarZone.WINDSHIELD)
        assertThat(items.first { it.id == "CABIN_FILTER" }.zone).isEqualTo(CarZone.CABIN)
    }
}
