package com.manhunt.app

import android.app.Application
import androidx.room.Room
import com.manhunt.app.data.local.AppDatabase
import org.osmdroid.config.Configuration
import java.io.File

class ManhuntApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // OSMDroid: user-agent is required by tile servers; private cache avoids storage-permission hell on modern Android
        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, getSharedPreferences("${packageName}_preferences", MODE_PRIVATE))
        osmConfig.userAgentValue = packageName
        val basePath = File(cacheDir, "osmdroid")
        val tilePath = File(basePath, "tiles")
        // Both directories must exist before any MapView is created; OSMDroid writes its tile cache
        // under osmdroidBasePath on init and fails to render if the path is missing.
        if (!basePath.exists()) basePath.mkdirs()
        if (!tilePath.exists()) tilePath.mkdirs()
        osmConfig.osmdroidBasePath = basePath
        osmConfig.osmdroidTileCache = tilePath
        // Prefer internal cache; do not rely on external storage (breaks on many devices without WRITE_EXTERNAL_STORAGE)
        try {
            osmConfig.isMapViewHardwareAccelerated = true
        } catch (_: Exception) {
        }

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "manhunt_local.db"
        ).fallbackToDestructiveMigration().build()
    }

    companion object {
        lateinit var instance: ManhuntApp
            private set
    }
}
