package com.healthsync.phone.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.healthsync.phone.data.FitnessDatabase
import com.healthsync.phone.data.LegacyDatabasePreservation
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    val migration1To6 = LegacyDatabasePreservation.migration(1)
    val migration2To6 = LegacyDatabasePreservation.migration(2)
    val migration3To6 = LegacyDatabasePreservation.migration(3)
    val migration5To6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sleep ADD COLUMN source TEXT NOT NULL DEFAULT 'LEGACY_UNVERIFIED'")
            db.execSQL("ALTER TABLE sleep ADD COLUMN estimatedMinutes INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE sleep ADD COLUMN stagesAvailable INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE workouts ADD COLUMN distanceSource TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE workouts ADD COLUMN caloriesEstimated INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE spo2 ADD COLUMN source TEXT NOT NULL DEFAULT 'LEGACY_UNVERIFIED'")
            // Earlier watch versions generated sleep durations/stages without captured epochs.
            db.execSQL("CREATE TABLE IF NOT EXISTS legacy_sleep_unverified AS SELECT * FROM sleep WHERE 0")
            db.execSQL("INSERT INTO legacy_sleep_unverified SELECT * FROM sleep WHERE source = 'LEGACY_UNVERIFIED'")
            db.execSQL("DELETE FROM sleep WHERE source = 'LEGACY_UNVERIFIED'")
            // Earlier oxygen screens used arbitrary values; their provenance cannot be recovered.
            db.execSQL("CREATE TABLE IF NOT EXISTS legacy_spo2_unverified AS SELECT * FROM spo2 WHERE 0")
            db.execSQL("INSERT INTO legacy_spo2_unverified SELECT * FROM spo2 WHERE source = 'LEGACY_UNVERIFIED'")
            db.execSQL("DELETE FROM spo2 WHERE source = 'LEGACY_UNVERIFIED'")
        }
    }
    val migration4To5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE workouts ADD COLUMN durationSeconds INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FitnessDatabase =
        Room.databaseBuilder(context, FitnessDatabase::class.java, "health_sync_db")
            .addMigrations(migration1To6, migration2To6, migration3To6, migration4To5, migration5To6)
            .build()
}
