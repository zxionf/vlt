package io.zx.password

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.plus

class PwdViewModel(private val repository: PwdRepository) : ViewModel() {

    sealed class UiState{
        object Loading : UiState()
        data class Success(val items: List<Pwd>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    private val _items = MutableStateFlow<List<Pwd>>(emptyList())
    val uiState : StateFlow<UiState> = _uiState.asStateFlow()
    val items : StateFlow<List<Pwd>> = _items.asStateFlow()

    init {
        loadItems()
    }

    private fun loadItems(){
        viewModelScope.launch {
            repository.getAll().collect { itemList ->
                // debug
                Log.d("PwdViewModel", "Loaded ${itemList.size} items")
                itemList.forEach { item ->
                    Log.d("PwdViewModel", "Item: $item")
                }

                _uiState.value = UiState.Success(itemList)
            }
        }
    }

    fun updateItem(updatedItem: Pwd) {
        _items.update { list ->
            list.map { if (it.id == updatedItem.id) updatedItem else it }
        }
    }

    fun addItem(newItem: Pwd) {
//        _items.update { list -> list + newItem }
        viewModelScope.launch {
            repository.insert(newItem)
            // 由于 getAllItems() 返回的是 Flow，插入后数据库会发出新数据，
            // loadItems 中的 collect 会自动收到，UI 会自动更新。
        }
    }
}