package com.example.mydmucabapp_driver

import android.app.Application
import android.content.Context

class MyApplication : Application() {

    override fun onTerminate() {
        super.onTerminate()
        clearCachedChatId()
    }


    private fun clearCachedChatId() {
        val sharedPreferences = getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()
    }
}
