package com.harisudhan.suras.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.harisudhan.suras.data.local.DatabaseKeyManager
import com.harisudhan.suras.data.local.PatientDao
import com.harisudhan.suras.data.local.SurasDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun preferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("settings") },
    )

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager,
    ): SurasDatabase {
        // SQLCipher 4 ships its own native library and must load it before the
        // first database handle is created.
        System.loadLibrary("sqlcipher")
        val passphrase = keyManager.passphrase()
        return Room.databaseBuilder(context, SurasDatabase::class.java, SurasDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            // No destructive fallback: a schema mismatch must never delete
            // encrypted patient records. Future versions add explicit
            // migrations here.
            .build()
    }

    @Provides
    fun patientDao(database: SurasDatabase): PatientDao = database.patientDao()
}
