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

    // Tags
    private val _allTags = MutableStateFlow<List<Tag>>(emptyList())
    val allTags: StateFlow<List<Tag>> = _allTags.asStateFlow()

    // Map of passwordId -> tags
    private val _tagMap = MutableStateFlow<Map<Long, List<Tag>>>(emptyMap())
    val tagMap: StateFlow<Map<Long, List<Tag>>> = _tagMap.asStateFlow()

    init {
        loadItems()
        loadTags()
    }

    private fun loadItems() {
        viewModelScope.launch {
            repository.getAll().collect { itemList ->
                Log.d("PwdViewModel", "Loaded ${itemList.size} items")
                _uiState.value = UiState.Success(itemList)
                _items.value = itemList
            }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            repository.getAllTags().collect { tags ->
                _allTags.value = tags
                refreshTagMap()
            }
        }
    }

    private fun refreshTagMap() {
        viewModelScope.launch {
            val map = mutableMapOf<Long, List<Tag>>()
            for (item in _items.value) {
                repository.getTagsForPassword(item.id).collect { tags ->
                    map[item.id] = tags
                    _tagMap.value = map.toMap()
                    return@collect
                }
            }
        }
    }

    // Password CRUD
    fun updateItem(updatedItem: PasswdEntity) {
        viewModelScope.launch { repository.update(updatedItem) }
    }

    fun addItem(newItem: PasswdEntity) {
        viewModelScope.launch { repository.insert(newItem) }
    }

    fun deleteItem(which: PasswdEntity) {
        viewModelScope.launch {
            repository.deleteJoinsForPassword(which.id)
            repository.delete(which)
        }
    }

    // Tag operations
    fun addTagToPassword(passwdId: Long, tagName: String) {
        viewModelScope.launch {
            val tag = repository.insertTag(tagName)
            repository.addTagToPassword(passwdId, tag.id)
            refreshTagMap()
        }
    }

    fun removeTagFromPassword(passwdId: Long, tagId: Long) {
        viewModelScope.launch {
            repository.removeTagFromPassword(passwdId, tagId)
            refreshTagMap()
        }
    }

    fun setTagsForPassword(passwdId: Long, tagNames: List<String>) {
        viewModelScope.launch {
            repository.setTagsForPassword(passwdId, tagNames)
            refreshTagMap()
        }
    }
}
