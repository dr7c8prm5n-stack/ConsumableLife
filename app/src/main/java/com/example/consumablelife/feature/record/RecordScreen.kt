package com.example.consumablelife.feature.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.consumablelife.R
import com.example.consumablelife.app.LocalUx
import com.example.consumablelife.core.data.ConsumableRepository
import com.example.consumablelife.core.data.DashboardData
import com.example.consumablelife.core.data.OdoSource
import com.example.consumablelife.core.designsystem.BigButton
import com.example.consumablelife.core.designsystem.BrandTitle
import com.example.consumablelife.core.designsystem.DateField
import com.example.consumablelife.core.designsystem.LocalStatusColors
import com.example.consumablelife.core.designsystem.SectionCard
import com.example.consumablelife.core.designsystem.Stepper
import com.example.consumablelife.core.designsystem.Tokens
import com.example.consumablelife.core.designsystem.formatNumber
import com.example.consumablelife.core.domain.CycleKind
import com.example.consumablelife.core.domain.RecordAction
import com.example.consumablelife.core.domain.RecordSource
import com.example.consumablelife.ui.date
import com.example.consumablelife.ui.distance
import com.example.consumablelife.ui.itemName
import com.example.consumablelife.ui.rememberCtx
import com.example.consumablelife.ui.verb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

sealed interface RecordCheck {
    data object Ok : RecordCheck
    data object FutureDate : RecordCheck
    data class OverOdometer(val currentKm: Double) : RecordCheck
    data class BeforePrevious(val prevKm: Double) : RecordCheck
}

/** FR-R03 검증 — 순수 함수 */
fun validateRecord(odoKm: Double, date: LocalDate, today: LocalDate, currentOdoKm: Double, previousOdoKm: Double?): RecordCheck = when {
    date.isAfter(today) -> RecordCheck.FutureDate
    odoKm > currentOdoKm + 0.5 -> RecordCheck.OverOdometer(currentOdoKm)
    previousOdoKm != null && odoKm < previousOdoKm -> RecordCheck.BeforePrevious(previousOdoKm)
    else -> RecordCheck.Ok
}

/** 저장 직후 S2 리필 애니메이션에 넘길 "저장 전" 값 */
data class SavedResult(val recordId: Long, val fromProgress: Float, val fromStatus: String, val fromCycleIndex: Int)

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val repo: ConsumableRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    val itemId: String = checkNotNull(handle["id"])
    val data: StateFlow<DashboardData?> = repo.dashboard.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(
        d: DashboardData, odoKm: Double, date: LocalDate, source: RecordSource, memo: String?,
        onSaved: (SavedResult) -> Unit,
    ) {
        val before = d.items.firstOrNull { it.item.id == itemId }
        val action = if (before?.kind == CycleKind.INSPECT) RecordAction.INSPECTED else RecordAction.REPLACED
        viewModelScope.launch {
            val id = repo.recordReplacement(itemId, odoKm, date, source, memo, action)
            onSaved(SavedResult(id, before?.cycle?.cycleProgress ?: 0f, before?.displayStatus?.name ?: "", before?.cycle?.cycleIndex ?: 1))
        }
    }
}

@Composable
fun RecordScreen(
    onBack: () -> Unit,
    onSaved: (itemId: String, result: SavedResult) -> Unit,
    vm: RecordViewModel = hiltViewModel(),
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val ctx = rememberCtx()
    val ux = LocalUx.current
    val d = data
    val state = d?.items?.firstOrNull { it.item.id == vm.itemId }

    var custom by rememberSaveable { mutableStateOf(false) }
    var dateEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var odo by rememberSaveable { mutableStateOf<Int?>(null) }
    var memo by rememberSaveable { mutableStateOf("") }
    var keyboardOdo by rememberSaveable { mutableStateOf(false) }
    var warn by rememberSaveable { mutableStateOf<Double?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(d?.odometer?.km) {
        if (odo == null && d != null) odo = d.odometer.km.roundToInt()
    }

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Gutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(Tokens.TouchTarget)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), Modifier.size(36.dp))
                }
                BrandTitle(
                    state?.let { stringResource(R.string.rec_title, itemName(ctx, it.item), verb(ctx, it.kind)) } ?: "",
                    titleStyle = MaterialTheme.typography.headlineSmall,
                )
            }
            if (d == null || state == null) return@Column
            val unit = d.settings.distanceUnit
            val today = LocalDate.now()
            val previous = state.records.filter { it.source != RecordSource.ESTIMATED }.maxOfOrNull { it.odometerKm }

            Column(Modifier.widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // FR-R01 기본값: 현재 Odometer + 오늘. [이대로 저장] 1탭 (키보드 불필요)
                SectionCard {
                    Text(stringResource(R.string.rec_default_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${date(today)} · ${distance(ctx, d.odometer.km, unit, round = false)}",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    BigButton(
                        stringResource(R.string.rec_save_now),
                        onClick = {
                            if (saving) return@BigButton
                            saving = true
                            val src = if (d.odometer.source == OdoSource.VEHICLE) RecordSource.VEHICLE_ODO else RecordSource.MANUAL
                            vm.save(d, d.odometer.km, today, src, null) { onSaved(vm.itemId, it) }
                        },
                        modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.TaskAlt, enabled = !saving,
                    )
                }

                // FR-R02 과거 기록 입력
                TextButton(
                    onClick = { custom = !custom },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.TouchTarget),
                ) {
                    Icon(if (custom) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                    Text(stringResource(R.string.rec_other), style = MaterialTheme.typography.titleSmall)
                }
                AnimatedVisibility(custom) {
                    SectionCard {
                        DateField(stringResource(R.string.rec_date), LocalDate.ofEpochDay(dateEpoch), { dateEpoch = it.toEpochDay() }, today)
                        Text(stringResource(R.string.rec_odometer), style = MaterialTheme.typography.titleSmall)
                        val maxOdo = d.odometer.km.roundToInt()
                        val cur = odo ?: maxOdo
                        Stepper(
                            stringResource(R.string.fmt_km, formatNumber(cur)), listOf(100, 1_000),
                            { delta -> odo = (cur + delta).coerceIn(0, maxOdo) },
                        )
                        // 키보드 입력은 보조 옵션 — 주차 중에만 (noKeyboard)
                        if (!ux.noKeyboard) {
                            if (keyboardOdo) {
                                OutlinedTextField(
                                    value = cur.toString(),
                                    onValueChange = { t -> t.filter(Char::isDigit).take(7).toIntOrNull()?.let { odo = it.coerceAtMost(maxOdo) } },
                                    label = { Text(stringResource(R.string.rec_odometer)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.titleMedium,
                                )
                            } else {
                                TextButton(onClick = { keyboardOdo = true }, modifier = Modifier.heightIn(min = 64.dp)) {
                                    Icon(Icons.Filled.Keyboard, null)
                                    Text(" " + stringResource(R.string.rec_keyboard), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            // MAY 메모 — 주차 중에만 노출
                            OutlinedTextField(
                                value = memo, onValueChange = { memo = it.take(40) },
                                label = { Text(stringResource(R.string.rec_memo)) },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.titleMedium,
                            )
                        } else {
                            Text(stringResource(R.string.rec_memo_parked), style = MaterialTheme.typography.bodySmall)
                        }

                        val selDate = LocalDate.ofEpochDay(dateEpoch)
                        val check = validateRecord(cur.toDouble(), selDate, today, d.odometer.km, previous)
                        when (check) {
                            RecordCheck.FutureDate -> ErrorText(stringResource(R.string.rec_err_future))
                            is RecordCheck.OverOdometer -> ErrorText(stringResource(R.string.rec_err_over_odo, distance(ctx, check.currentKm, unit, false)))
                            else -> Unit
                        }
                        BigButton(
                            stringResource(R.string.rec_save),
                            onClick = {
                                when (check) {
                                    is RecordCheck.BeforePrevious -> warn = check.prevKm
                                    RecordCheck.Ok -> {
                                        saving = true
                                        vm.save(d, cur.toDouble(), selDate, RecordSource.MANUAL, memo) { onSaved(vm.itemId, it) }
                                    }
                                    else -> Unit
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !saving && (check == RecordCheck.Ok || check is RecordCheck.BeforePrevious),
                        )
                    }
                }
            }

            warn?.let { prev ->
                AlertDialog(
                    onDismissRequest = { warn = null },
                    text = { Text(stringResource(R.string.rec_warn_before_prev, distance(ctx, prev, unit, false)), style = MaterialTheme.typography.bodyLarge) },
                    confirmButton = {
                        TextButton(onClick = {
                            warn = null; saving = true
                            vm.save(d, (odo ?: 0).toDouble(), LocalDate.ofEpochDay(dateEpoch), RecordSource.MANUAL, memo) { onSaved(vm.itemId, it) }
                        }, Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.rec_confirm), style = MaterialTheme.typography.labelLarge) }
                    },
                    dismissButton = {
                        TextButton(onClick = { warn = null }, Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Box(Modifier.fillMaxWidth()) {
        Text(text, color = LocalStatusColors.current.overdue, style = MaterialTheme.typography.bodyMedium)
    }
}
