package io.zx.password

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PwdViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PwdViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PwdViewModel(
                PwdRepository(PwdDB.getInstance(context).PwdDao())
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}