package io.zx.password

// PwdViewModelFactory.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context

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