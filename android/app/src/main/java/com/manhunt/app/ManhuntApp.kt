package com.manhunt.app

import android.app.Application
import android.preference.PreferenceManager
import androidx.room.Room
import com.manhunt.app.data.local.AppDatabase
import org.osmdroid.config.Configuration

class ManhuntApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Configure OSMDroid user agent & tile storage
        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        Configuration.getInstance().userAgentValue = packageName

        // Initialize Room DB
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
