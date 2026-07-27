package io.zx.password

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PwdViewModel(private val repository: PwdRepository) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Success(val items: List<PasswordEntry>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    private val _items = MutableStateFlow<List<PasswordEntry>>(emptyList())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val items: StateFlow<List<PasswordEntry>> = _items.asStateFlow()

    private val _allTags = MutableStateFlow<List<Tag>>(emptyList())
    val allTags: StateFlow<List<Tag>> = _allTags.asStateFlow()

    private val _tagMap = MutableStateFlow<Map<String, List<Tag>>>(emptyMap())
    val tagMap: StateFlow<Map<String, List<Tag>>> = _tagMap.asStateFlow()

    private val _selectedTagId = MutableStateFlow<Long?>(null)
    val selectedTagId: StateFlow<Long?> = _selectedTagId.asStateFlow()

    private val _currentDeviceId = MutableStateFlow("")
    val currentDeviceId: StateFlow<String> = _currentDeviceId.asStateFlow()

    init {
        loadItems()
        loadTags()
        loadCurrentDeviceId()
    }

    private fun loadItems() {
        viewModelScope.launch {
            repository.getAll().collect { itemList ->
                Log.d("PwdViewModel", "Loaded ${itemList.size} items")
                _uiState.value = UiState.Success(itemList)
                _items.value = itemList
                refreshTagMap()
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

    private fun loadCurrentDeviceId() {
        viewModelScope.launch {
            _currentDeviceId.value = repository.getCurrentDeviceId() ?: ""
        }
    }

    private fun refreshTagMap() {
        viewModelScope.launch {
            val map = mutableMapOf<String, List<Tag>>()
            for (item in _items.value) {
                val tags = repository.getTagsForPassword(item.id).first()
                map[item.id] = tags
            }
            _tagMap.value = map
        }
    }

    fun updateItem(updatedItem: PasswordEntry) {
        viewModelScope.launch { repository.update(updatedItem) }
    }

    fun addItem(newItem: PasswordEntry) {
        viewModelScope.launch { repository.insert(newItem) }
    }

    fun deleteItem(which: PasswordEntry) {
        viewModelScope.launch {
            repository.deleteJoinsForPassword(which.id)
            repository.delete(which)
        }
    }

    fun addTagToPassword(passwordId: String, tagName: String) {
        viewModelScope.launch {
            val tag = repository.insertTag(tagName)
            repository.addTagToPassword(passwordId, tag.id)
            refreshTagMap()
        }
    }

    fun removeTagFromPassword(passwordId: String, tagId: Long) {
        viewModelScope.launch {
            repository.removeTagFromPassword(passwordId, tagId)
            refreshTagMap()
        }
    }

    fun setTagsForPassword(passwordId: String, tagNames: List<String>) {
        viewModelScope.launch {
            repository.setTagsForPassword(passwordId, tagNames)
            refreshTagMap()
        }
    }

    fun setSelectedTag(tagId: Long?) {
        _selectedTagId.value = if (_selectedTagId.value == tagId) null else tagId
    }

    fun createTag(name: String) {
        viewModelScope.launch {
            repository.insertTag(name)
        }
    }

    fun updateTag(tag: Tag) {
        viewModelScope.launch {
            repository.updateTag(tag)
        }
    }

    fun deleteTag(tag: Tag) {
        viewModelScope.launch {
            repository.deleteTag(tag)
        }
    }
}
