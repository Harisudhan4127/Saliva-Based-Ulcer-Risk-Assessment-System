package com.harisudhan.suras.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PatientRecord::class],
    version = 1,
    exportSchema = true,
)
abstract class SurasDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao

    companion object {
        const val NAME = "suras.db"
    }
}
