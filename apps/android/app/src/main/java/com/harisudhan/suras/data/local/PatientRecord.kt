package com.harisudhan.suras.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved assessment. Field names mirror the 14 inputs in the desktop app
 * plus the model output and version provenance so an exported report can be
 * reproduced without the original session.
 */
@Entity(
    tableName = "assessments",
    indices = [
        Index("createdAt"),
        Index("name"),
        Index("patientId"),
    ],
)
data class PatientRecord(
    @PrimaryKey val patientId: String,
    val name: String,
    val age: Int,
    val gender: String,
    val spicyFood: Boolean,
    val tobacco: Boolean,
    val alcohol: Boolean,
    val sleepHours: Double,
    val stress: Int,
    val skipMeals: Boolean,
    val softDrinks: Boolean,
    val emptyStomachPain: Boolean,
    val day1PhBefore: Double,
    val day1PhAfter: Double,
    val day2PhBefore: Double,
    val day2PhAfter: Double,
    val probability: Double,
    val riskLevel: String,
    val swbraStatus: String,
    val swbraScore: Int?,
    val swbraCategory: String?,
    val appVersion: String,
    val modelVersion: String,
    val preprocessingVersion: String,
    val swbraSpecVersion: String,
    val warningsJson: String,
    val createdAt: Long,
)
