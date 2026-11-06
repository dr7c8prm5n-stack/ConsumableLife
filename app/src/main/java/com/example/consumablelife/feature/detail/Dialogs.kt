package com.example.consumablelife.feature.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.consumablelife.R
import com.example.consumablelife.core.designsystem.ChoiceRow
import com.example.consumablelife.core.designsystem.DateField
import com.example.consumablelife.core.designsystem.Stepper
import com.example.consumablelife.core.designsystem.formatNumber
import com.example.consumablelife.core.domain.Condition
import com.example.consumablelife.core.domain.ConsumableItem
import com.example.consumablelife.core.domain.Interval
import com.example.consumablelife.core.domain.ReplacementRecord
import com.example.consumablelife.ui.itemName
import com.example.consumablelife.ui.rememberCtx
import java.time.LocalDate
import kotlin.math.roundToInt

/** S2 [주기 수정] / S4 항목별 주기 편집 — 스테퍼만으로 편집 (키보드 불필요) */
@Composable
fun IntervalDialog(
    item: ConsumableItem,
    initialCondition: Condition,
    onDismiss: () -> Unit,
    onSave: (ConsumableItem) -> Unit,
) {
    val ctx = rememberCtx()
    var cond by rememberSaveable { mutableStateOf(initialCondition) }
    var nKm by rememberSaveable { mutableStateOf(item.normal.km) }
    var nMo by rememberSaveable { mutableStateOf(item.normal.months) }
    var sKm by rememberSaveable { mutableStateOf(item.severe.km) }
    var sMo by rememberSaveable { mutableStateOf(item.severe.months) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.interval_title, itemName(ctx, item)), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ChoiceRow(
                    listOf(Condition.NORMAL, Condition.SEVERE), cond,
                    { stringResource(if (it == Condition.NORMAL) R.string.interval_normal else R.string.interval_severe) },
                    { cond = it },
                )
                val km = if (cond == Condition.NORMAL) nKm else sKm
                val mo = if (cond == Condition.NORMAL) nMo else sMo
                OptionalStepper(
                    label = stringResource(R.string.interval_km),
                    value = km,
                    text = { stringResource(R.string.fmt_km, formatNumber(it)) },
                    default = 10_000, steps = listOf(1_000, 5_000), min = 1_000,
                    onChange = { if (cond == Condition.NORMAL) nKm = it else sKm = it },
                )
                OptionalStepper(
                    label = stringResource(R.string.interval_months),
                    value = mo,
                    text = { stringResource(R.string.fmt_months, it) },
                    default = 12, steps = listOf(1, 6), min = 1,
                    onChange = { if (cond == Condition.NORMAL) nMo = it else sMo = it },
                )
                Text(stringResource(R.string.manual_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // DG2-I2: 사용자가 주기를 바꾸면 "기본값 · 수정 가능" 배지 제거
                onSave(item.copy(normal = Interval(nKm, nMo), severe = Interval(sKm, sMo), needsConfirm = false, isUserDefault = false))
            }, Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.done), style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge) }
        },
    )
}

@Composable
private fun OptionalStepper(
    label: String,
    value: Int?,
    text: @Composable (Int) -> String,
    default: Int,
    steps: List<Int>,
    min: Int,
    onChange: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = value != null, onCheckedChange = { onChange(if (it) default else null) })
        }
        if (value != null) {
            Stepper(text(value), steps, { d -> onChange((value + d).coerceAtLeast(min)) })
        } else {
            Text(stringResource(R.string.interval_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 이력 행 수정 (S2 길게 누르기) */
@Composable
fun RecordEditDialog(
    record: ReplacementRecord,
    maxOdoKm: Double,
    onDismiss: () -> Unit,
    onSave: (ReplacementRecord) -> Unit,
) {
    var date by rememberSaveable { mutableStateOf(record.date.toEpochDay()) }
    var odo by rememberSaveable { mutableStateOf(record.odometerKm.roundToInt()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_edit), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DateField(stringResource(R.string.rec_date), LocalDate.ofEpochDay(date), { date = it.toEpochDay() }, LocalDate.now())
                Text(stringResource(R.string.rec_odometer), style = MaterialTheme.typography.titleSmall)
                Stepper(
                    stringResource(R.string.fmt_km, formatNumber(odo)), listOf(100, 1_000),
                    { d -> odo = (odo + d).coerceIn(0, maxOdoKm.roundToInt()) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(record.copy(date = LocalDate.ofEpochDay(date), odometerKm = odo.toDouble()))
            }, Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.done), style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge) }
        },
    )
}
