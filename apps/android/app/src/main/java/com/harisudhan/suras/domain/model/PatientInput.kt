package com.harisudhan.suras.domain.model

import org.json.JSONObject

/** The 14 inputs collected by the desktop app, kept as strings until the shared
 *  Rust core validates them. Numeric strings are sent as numbers when they parse
 *  to a finite value so the core can distinguish a wrong type from a bad range.
 */
data class PatientInput(
    val patientCode: String,
    val name: String,
    val age: String,
    val gender: String,
    val spicyFood: String,
    val tobacco: String,
    val alcohol: String,
    val sleepHours: String,
    val stress: String,
    val skipMeals: String,
    val softDrinks: String,
    val emptyStomachPain: String,
    val day1PhBefore: String,
    val day1PhAfter: String,
    val day2PhBefore: String,
    val day2PhAfter: String,
) {
    fun toPayloadJson(): String = JSONObject().apply {
        putNumberOrRaw("Age", age)
        put("Gender", gender)
        put("Spicy_Food", spicyFood)
        put("Tobacco", tobacco)
        put("Alcohol", alcohol)
        putNumberOrRaw("Sleep_Hours", sleepHours)
        putNumberOrRaw("Stress", stress)
        put("Skip_Meals", skipMeals)
        put("Soft_Drinks", softDrinks)
        put("Empty_Stomach_Pain", emptyStomachPain)
        putNumberOrRaw("Day1_pH_Before", day1PhBefore)
        putNumberOrRaw("Day1_pH_After", day1PhAfter)
        putNumberOrRaw("Day2_pH_Before", day2PhBefore)
        putNumberOrRaw("Day2_pH_After", day2PhAfter)
    }.toString()

    companion object {
        const val YES = "Yes"
        const val NO = "No"
        const val MALE = "Male"
        const val FEMALE = "Female"

        fun yesNo(value: Boolean): String = if (value) YES else NO
        fun isYes(value: String): Boolean = value == YES
    }
}

private fun JSONObject.putNumberOrRaw(key: String, raw: String) {
    val trimmed = raw.trim()
    val number = trimmed.toDoubleOrNull()
    if (number != null && number.isFinite()) {
        put(key, number)
    } else {
        put(key, trimmed)
    }
}
