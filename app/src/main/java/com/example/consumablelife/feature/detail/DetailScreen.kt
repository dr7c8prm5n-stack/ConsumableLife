package com.example.consumablelife.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.consumablelife.core.data.ConsumableRepository
import com.example.consumablelife.core.domain.ConsumableItem
import com.example.consumablelife.core.domain.ReplacementRecord
import kotlinx.coroutines.launch

/**
 * 탭 화면(상세 카드·상세 패널·상세 시트)이 공유하는 쓰기 동작.
 * 설계서 v3: 항목 상세는 별도 화면이 아니라 Modal Bottom Sheet(깊이 2)로 열린다 — feature/consumables/ConsumablesScreen.kt
 */
abstract class ItemActionsViewModel(protected val repo: ConsumableRepository) : ViewModel() {
    fun saveItem(item: ConsumableItem) = viewModelScope.launch { repo.updateItem(item) }
    fun updateRecord(r: ReplacementRecord) = viewModelScope.launch { repo.updateRecord(r) }
    fun deleteRecord(id: Long) = viewModelScope.launch { repo.deleteRecord(id) }
}
