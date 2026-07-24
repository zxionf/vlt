package io.zx.password

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PwdViewModel(private val repository: PwdRepository) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Success(val items: List<PasswdEntity>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    private val _items = MutableStateFlow<List<PasswdEntity>>(emptyList())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val items: StateFlow<List<PasswdEntity>> = _items.asStateFlow()

    init {
        loadItems()
    }

    private fun loadItems() {
        viewModelScope.launch {
            repository.getAll().collect { itemList ->
                Log.d("PwdViewModel", "Loaded ${itemList.size} items")
                _uiState.value = UiState.Success(itemList)
            }
        }
    }

    fun updateItem(updatedItem: PasswdEntity) {
        viewModelScope.launch {
            repository.update(updatedItem)
        }
    }

    fun addItem(newItem: PasswdEntity) {
        viewModelScope.launch {
            repository.insert(newItem)
        }
    }

    fun deleteItem(which: PasswdEntity) {
        viewModelScope.launch {
            repository.delete(which)
        }
    }
}
