package com.harisudhan.suras.domain.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientInputTest {

    private fun sampleInput() = PatientInput(
        patientCode = "PAT-12345678",
        name = "Test Patient",
        age = "34",
        gender = PatientInput.FEMALE,
        spicyFood = PatientInput.YES,
        tobacco = PatientInput.NO,
        alcohol = PatientInput.NO,
        sleepHours = "7.5",
        stress = "4",
        skipMeals = PatientInput.NO,
        softDrinks = PatientInput.YES,
        emptyStomachPain = PatientInput.NO,
        day1PhBefore = "6.4",
        day1PhAfter = "5.1",
        day2PhBefore = "6.5",
        day2PhAfter = "5.0",
    )

    @Test
    fun payloadContainsEveryModelField() {
        val json = JSONObject(sampleInput().toPayloadJson())
        assertEquals(14, json.length())
        assertEquals(34.0, json.getDouble("Age"), 0.0)
        assertEquals("Female", json.getString("Gender"))
        assertEquals("Yes", json.getString("Spicy_Food"))
        assertEquals("No", json.getString("Tobacco"))
        assertEquals(7.5, json.getDouble("Sleep_Hours"), 0.0)
        assertEquals(4.0, json.getDouble("Stress"), 0.0)
        assertEquals(6.4, json.getDouble("Day1_pH_Before"), 0.0)
        assertEquals(5.0, json.getDouble("Day2_pH_After"), 0.0)
    }

    @Test
    fun blankAndNonNumericValuesStayStringsSoTheCoreRejectsThem() {
        val json = JSONObject(sampleInput().copy(age = "", sleepHours = "abc").toPayloadJson())
        assertEquals("", json.getString("Age"))
        assertEquals("abc", json.getString("Sleep_Hours"))
    }

    @Test
    fun commaDecimalIsNormalisedBeforeSerialisation() {
        val json = JSONObject(sampleInput().copy(day1PhBefore = "6,4".replace(',', '.')).toPayloadJson())
        assertEquals(6.4, json.getDouble("Day1_pH_Before"), 0.0)
    }

    @Test
    fun yesNoHelpersRoundTrip() {
        assertTrue(PatientInput.isYes(PatientInput.yesNo(true)))
        assertFalse(PatientInput.isYes(PatientInput.yesNo(false)))
        assertEquals("Yes", PatientInput.yesNo(true))
        assertEquals("No", PatientInput.yesNo(false))
    }
}
