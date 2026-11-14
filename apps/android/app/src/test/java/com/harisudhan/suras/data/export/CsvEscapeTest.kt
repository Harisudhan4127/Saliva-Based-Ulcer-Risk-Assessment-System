package com.harisudhan.suras.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvEscapeTest {

    @Test
    fun plainValuesAreNotQuoted() {
        assertEquals("PAT-1234", ReportExporter.escapeCsv("PAT-1234"))
        assertEquals("6.4", ReportExporter.escapeCsv("6.4"))
    }

    @Test
    fun commasQuotesAndNewlinesAreEscaped() {
        assertEquals("\"a,b\"", ReportExporter.escapeCsv("a,b"))
        assertEquals("\"a\"\"b\"", ReportExporter.escapeCsv("a\"b"))
        assertEquals("\"line1\nline2\"", ReportExporter.escapeCsv("line1\nline2"))
        assertEquals("\"line1\rline2\"", ReportExporter.escapeCsv("line1\rline2"))
    }

    @Test
    fun headerHasOneColumnPerField() {
        assertEquals(25, ReportExporter.CSV_HEADER.size)
        assertEquals("patient_id", ReportExporter.CSV_HEADER.first())
        assertEquals("created_at", ReportExporter.CSV_HEADER.last())
    }
}
