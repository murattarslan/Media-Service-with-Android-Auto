package com.murattarslan.myautoapp

import android.app.Application
import com.murattarslan.car.core.MediaService

class MyApp: Application() {
    override fun onCreate() {
        super.onCreate()
        MediaService.builder(this).build()
    }
}