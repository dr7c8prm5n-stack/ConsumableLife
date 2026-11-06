package com.example.consumablelife.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** UX-13 76dp 터치 타깃 기본 버튼 */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    primary: Boolean = true,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null, Modifier.size(28.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
    if (primary) {
        Button(
            onClick, modifier.heightIn(min = Tokens.TouchTarget), enabled = enabled,
            shape = RoundedCornerShape(Tokens.ButtonRadius), content = content,
        )
    } else {
        // 보조 버튼: 주색 1.5dp 외곽선 + 주색 글자 (현대차 아웃라인 버튼 톤)
        OutlinedButton(
            onClick, modifier.heightIn(min = Tokens.TouchTarget), enabled = enabled,
            shape = RoundedCornerShape(Tokens.ButtonRadius),
            border = BorderStroke(1.5.dp, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            content = content,
        )
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Tokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = glassSurface()),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/**
 * FR-R02 스테퍼: 큰 ± 버튼, 길게 누르면 가속. 키보드 불필요.
 */
@Composable
fun Stepper(
    valueText: String,
    steps: List<Int>,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(valueText, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.TouchGap)) {
            steps.filter { it > 0 }.sortedDescending().forEach { s ->
                RepeatButton("−${formatNumber(s)}", enabled) { onStep(-s) }
            }
            steps.sorted().filter { it > 0 }.forEach { s ->
                RepeatButton("+${formatNumber(s)}", enabled) { onStep(s) }
            }
        }
    }
}

@Composable
private fun RepeatButton(label: String, enabled: Boolean, onStep: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        repeated = false
        delay(450)
        var d = 160L
        while (isActive) {
            repeated = true
            step()
            delay(d)
            d = (d * 0.85).toLong().coerceAtLeast(35L)
        }
    }
    OutlinedButton(
        onClick = { if (!repeated) step(); repeated = false },
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier.defaultMinSize(minWidth = 96.dp, minHeight = Tokens.TouchTarget),
        shape = RoundedCornerShape(Tokens.ControlRadius),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) { Text(label, style = MaterialTheme.typography.titleSmall) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate,
    onDate: (LocalDate) -> Unit,
    maxDate: LocalDate,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth().heightIn(min = Tokens.TouchTarget),
        shape = RoundedCornerShape(Tokens.ControlRadius),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Icon(Icons.Filled.CalendarMonth, null, Modifier.size(28.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        Text("$label  ${date}", style = MaterialTheme.typography.titleSmall)
    }
    if (open) {
        val maxMillis = maxDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= maxMillis // FR-R03 미래 날짜 금지
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text(androidx.compose.ui.res.stringResource(com.example.consumablelife.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(androidx.compose.ui.res.stringResource(com.example.consumablelife.R.string.cancel))
                }
            },
        ) { DatePicker(state) }
    }
}

/** 2~3개 큰 선택지 (온보딩 파워트레인/조건 등) */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                modifier = Modifier.heightIn(min = Tokens.TouchTarget).widthIn(min = 96.dp),
                label = { Text(label(o), style = MaterialTheme.typography.titleSmall, maxLines = 1) },
            )
        }
    }
}
