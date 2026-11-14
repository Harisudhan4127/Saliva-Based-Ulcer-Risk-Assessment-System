package com.harisudhan.suras.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun newerPatchVersionWins() {
        assertTrue(SemVer.isNewer("2.1.1", "2.1.0"))
        assertEquals(1, SemVer.compare("2.1.1", "2.1.0"))
    }

    @Test
    fun prefixAndBuildMetadataAreIgnored() {
        assertEquals(0, SemVer.compare("v2.1.0", "2.1.0+build.7"))
        assertEquals(0, SemVer.compare("V2.1.0", "2.1.0"))
    }

    @Test
    fun releaseSortsAbovePreRelease() {
        assertTrue(SemVer.isNewer("2.1.0", "2.1.0-rc.1"))
        assertFalse(SemVer.isNewer("2.1.0-rc.1", "2.1.0"))
        assertTrue(SemVer.isNewer("2.1.0-rc.2", "2.1.0-rc.1"))
    }

    @Test
    fun missingComponentsAreZero() {
        assertEquals(0, SemVer.compare("2.1", "2.1.0"))
        assertTrue(SemVer.isNewer("2.1.1", "2.1"))
    }

    @Test
    fun malformedVersionsNeverTriggerAnUpdate() {
        assertFalse(SemVer.isNewer("not-a-version", "2.1.0"))
        assertFalse(SemVer.isNewer("2.1.0", "not-a-version"))
        assertEquals(0, SemVer.compare("", "2.1.0"))
    }
}
