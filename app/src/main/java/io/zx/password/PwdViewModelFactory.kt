package io.zx.password

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PwdViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PwdViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            val db = PwdDB.getInstance(context)
            return PwdViewModel(
                PwdRepository(db.PwdDao(), db.TagDao(), db.DeviceDao())
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}