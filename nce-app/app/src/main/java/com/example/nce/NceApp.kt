package com.example.nce

import android.app.Application
import com.example.nce.data.asset.BookImporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NceApp : Application() {
    @Inject lateinit var bookImporter: BookImporter

    override fun onCreate() {
        super.onCreate()
        bookImporter.ensureImported()
    }
}
