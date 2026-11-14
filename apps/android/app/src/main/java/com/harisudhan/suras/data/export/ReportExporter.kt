package com.harisudhan.suras.data.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.harisudhan.suras.R
import com.harisudhan.suras.data.local.PatientRecord
import com.harisudhan.suras.data.repository.PatientRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Creates CSV and PDF exports in the app cache. Nothing is uploaded; the user
 * chooses the receiving app through the system share sheet. Every PDF page
 * repeats the research-only disclaimer.
 */
@Singleton
class ReportExporter @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: PatientRepository,
) {
    private val appContext = context.applicationContext

    suspend fun exportAllCsv(): File? = withContext(Dispatchers.IO) {
        val records = repository.all()
        if (records.isEmpty()) return@withContext null
        val file = File(exportDir(), appContext.getString(
            R.string.export_filename_csv,
            timestamp(),
        ))
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write(CSV_HEADER.joinToString(","))
            writer.write("\r\n")
            records.forEach { record ->
                writer.write(csvRow(record))
                writer.write("\r\n")
            }
        }
        file
    }

    suspend fun exportAllPdf(): File? = withContext(Dispatchers.IO) {
        val records = repository.all()
        if (records.isEmpty()) return@withContext null
        val file = File(exportDir(), appContext.getString(
            R.string.export_filename_pdf,
            timestamp(),
        ))
        writePdf(file, records)
        file
    }

    suspend fun exportRecordPdf(record: PatientRecord): File = withContext(Dispatchers.IO) {
        val file = File(exportDir(), appContext.getString(
            R.string.export_single_pdf,
            record.patientId,
        ))
        writePdf(file, listOf(record))
        file
    }

    suspend fun exportRecordCsv(record: PatientRecord): File = withContext(Dispatchers.IO) {
        val file = File(exportDir(), appContext.getString(
            R.string.export_single_csv,
            record.patientId,
        ))
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write(CSV_HEADER.joinToString(","))
            writer.write("\r\n")
            writer.write(csvRow(record))
            writer.write("\r\n")
        }
        file
    }

    fun share(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(appContext.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(
            intent,
            appContext.getString(R.string.share_chooser),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(chooser)
    }

    private fun writePdf(file: File, records: List<PatientRecord>) {
        val document = PdfDocument()
        val writer = PdfWriter(document)
        try {
            writer.beginPage()
            writer.title(appContext.getString(R.string.export_report_title))
            writer.subtitle(
                appContext.getString(R.string.export_report_generated, displayDate(Date())),
            )
            writer.spacer(6f)
            writer.disclaimer(appContext.getString(R.string.result_disclaimer))
            writer.spacer(10f)

            records.forEach { record ->
                writer.record(record)
            }
            writer.finish()
            file.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private inner class PdfWriter(private val document: PdfDocument) {
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f
        private var pageNumber = 0

        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 118, 110)
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 9.5f
        }
        private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8f
        }
        private val riskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        fun beginPage() {
            if (page != null) finishPage()
            pageNumber += 1
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(info)
            canvas = page!!.canvas
            y = MARGIN
        }

        fun title(text: String) {
            canvas!!.drawText(text, MARGIN, y + titlePaint.textSize, titlePaint)
            y += titlePaint.textSize + 8f
        }

        fun subtitle(text: String) {
            canvas!!.drawText(text, MARGIN, y + mutedPaint.textSize, mutedPaint)
            y += mutedPaint.textSize + 4f
        }

        fun spacer(height: Float) {
            y += height
        }

        fun disclaimer(text: String) {
            val contentWidth = PAGE_WIDTH - MARGIN * 2
            val lines = wrap(text, mutedPaint, contentWidth - 16f)
            val boxHeight = lines.size * (mutedPaint.textSize + 2f) + 12f
            canvas!!.drawRect(
                MARGIN,
                y,
                PAGE_WIDTH - MARGIN,
                y + boxHeight,
                Paint().apply { color = Color.rgb(248, 250, 252) },
            )
            var lineY = y + 10f + mutedPaint.textSize
            lines.forEach { line ->
                canvas!!.drawText(line, MARGIN + 8f, lineY, mutedPaint)
                lineY += mutedPaint.textSize + 2f
            }
            y += boxHeight + 4f
        }

        fun record(record: PatientRecord) {
            ensureSpace(420f)
            canvas!!.drawLine(
                MARGIN,
                y,
                PAGE_WIDTH - MARGIN,
                y,
                Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 1f },
            )
            y += 12f

            canvas!!.drawText(record.name, MARGIN, y + 13f, titlePaint.apply { textSize = 13f })
            canvas!!.drawText(record.patientId, MARGIN, y + 26f, mutedPaint)
            val assessed = appContext.getString(
                R.string.result_timestamp,
                displayDate(Date(record.createdAt)),
            )
            val assessedWidth = mutedPaint.measureText(assessed)
            canvas!!.drawText(assessed, PAGE_WIDTH - MARGIN - assessedWidth, y + 26f, mutedPaint)
            y += 38f

            val riskColor = when (record.riskLevel) {
                "HIGH" -> Color.rgb(220, 38, 38)
                "MEDIUM" -> Color.rgb(217, 119, 6)
                else -> Color.rgb(22, 163, 74)
            }
            riskPaint.color = riskColor
            canvas!!.drawText(
                appContext.getString(R.string.export_pdf_risk, record.riskLevel),
                MARGIN,
                y + 10f,
                riskPaint,
            )
            canvas!!.drawText(
                appContext.getString(
                    R.string.result_probability,
                    String.format(Locale.US, "%.1f", record.probability * 100),
                ),
                MARGIN + 170f,
                y + 10f,
                bodyPaint,
            )
            y += 22f

            canvas!!.drawText(
                appContext.getString(R.string.result_swbra_title),
                MARGIN,
                y + 8f,
                headingPaint,
            )
            val swbraScore = record.swbraScore?.let { it.toString() }
                ?: appContext.getString(R.string.result_swbra_unavailable)
            canvas!!.drawText(
                appContext.getString(R.string.export_pdf_swbra, swbraScore, record.swbraStatus),
                MARGIN + 110f,
                y + 8f,
                bodyPaint,
            )
            y += 18f

            canvas!!.drawText(
                appContext.getString(R.string.result_inputs),
                MARGIN,
                y + 8f,
                headingPaint,
            )
            y += 16f
            val inputs = inputLines(record)
            inputs.chunked(2).forEach { pair ->
                pair.forEachIndexed { index, line ->
                    val x = MARGIN + index * (COLUMN_WIDTH + 12f)
                    canvas!!.drawText(line, x, y + bodyPaint.textSize, bodyPaint)
                }
                y += bodyPaint.textSize + 4f
            }

            y += 6f
            canvas!!.drawText(
                appContext.getString(R.string.result_versions),
                MARGIN,
                y + 8f,
                headingPaint,
            )
            y += 14f
            val versions = appContext.getString(
                R.string.export_pdf_versions,
                record.appVersion,
                record.modelVersion,
                record.preprocessingVersion,
                record.swbraSpecVersion,
            )
            wrap(versions, mutedPaint, PAGE_WIDTH - MARGIN * 2).forEach { line ->
                canvas!!.drawText(line, MARGIN, y + mutedPaint.textSize, mutedPaint)
                y += mutedPaint.textSize + 2f
            }
            y += 16f
        }

        private fun inputLines(record: PatientRecord): List<String> {
            fun yesNo(value: Boolean): String =
                appContext.getString(if (value) R.string.yes else R.string.no)
            return listOf(
                appContext.getString(R.string.field_age) to record.age.toString(),
                appContext.getString(R.string.field_gender) to record.gender,
                appContext.getString(R.string.field_spicy_food) to yesNo(record.spicyFood),
                appContext.getString(R.string.field_tobacco) to yesNo(record.tobacco),
                appContext.getString(R.string.field_alcohol) to yesNo(record.alcohol),
                appContext.getString(R.string.field_sleep_hours) to record.sleepHours.toString(),
                appContext.getString(R.string.field_stress) to record.stress.toString(),
                appContext.getString(R.string.field_skip_meals) to yesNo(record.skipMeals),
                appContext.getString(R.string.field_soft_drinks) to yesNo(record.softDrinks),
                appContext.getString(R.string.field_empty_stomach_pain) to
                    yesNo(record.emptyStomachPain),
                appContext.getString(R.string.field_day1_ph_before) to
                    record.day1PhBefore.toString(),
                appContext.getString(R.string.field_day1_ph_after) to
                    record.day1PhAfter.toString(),
                appContext.getString(R.string.field_day2_ph_before) to
                    record.day2PhBefore.toString(),
                appContext.getString(R.string.field_day2_ph_after) to
                    record.day2PhAfter.toString(),
            ).map { (label, value) -> "$label: $value" }
        }

        private fun ensureSpace(required: Float) {
            if (y + required > PAGE_HEIGHT - FOOTER_HEIGHT) beginPage()
        }

        fun finish() {
            finishPage()
        }

        private fun finishPage() {
            val activePage = page ?: return
            val activeCanvas = canvas ?: return
            drawFooter(activeCanvas)
            document.finishPage(activePage)
            page = null
            canvas = null
        }

        private fun drawFooter(canvas: Canvas) {
            val footerY = PAGE_HEIGHT - 24f
            canvas.drawLine(
                MARGIN,
                footerY - 10f,
                PAGE_WIDTH - MARGIN,
                footerY - 10f,
                Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 1f },
            )
            canvas.drawText(
                appContext.getString(R.string.export_pdf_footer),
                MARGIN,
                footerY,
                mutedPaint,
            )
            val pageText = appContext.getString(R.string.export_pdf_page, pageNumber)
            canvas.drawText(
                pageText,
                PAGE_WIDTH - MARGIN - mutedPaint.measureText(pageText),
                footerY,
                mutedPaint,
            )
        }

        private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            val words = text.split(' ')
            val lines = mutableListOf<String>()
            var current = StringBuilder()
            words.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) lines += current.toString()
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
            return lines.ifEmpty { listOf("") }
        }
    }

    private fun csvRow(record: PatientRecord): String = listOf(
        record.patientId,
        record.name,
        record.age.toString(),
        record.gender,
        yesNo(record.spicyFood),
        yesNo(record.tobacco),
        yesNo(record.alcohol),
        record.sleepHours.toString(),
        record.stress.toString(),
        yesNo(record.skipMeals),
        yesNo(record.softDrinks),
        yesNo(record.emptyStomachPain),
        record.day1PhBefore.toString(),
        record.day1PhAfter.toString(),
        record.day2PhBefore.toString(),
        record.day2PhAfter.toString(),
        String.format(Locale.US, "%.6f", record.probability),
        record.riskLevel,
        record.swbraStatus,
        record.swbraScore?.toString() ?: "",
        record.appVersion,
        record.modelVersion,
        record.preprocessingVersion,
        record.swbraSpecVersion,
        isoDate(record.createdAt),
    ).joinToString(",") { escapeCsv(it) }

    private fun yesNo(value: Boolean): String =
        appContext.getString(if (value) R.string.yes else R.string.no)

    private fun exportDir(): File = File(appContext.cacheDir, "exports").apply { mkdirs() }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private fun isoDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(millis))

    private fun displayDate(date: Date): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(date)

    internal companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val FOOTER_HEIGHT = 40f
        const val COLUMN_WIDTH = 245f

        val CSV_HEADER = listOf(
            "patient_id",
            "name",
            "age",
            "gender",
            "spicy_food",
            "tobacco",
            "alcohol",
            "sleep_hours",
            "stress",
            "skip_meals",
            "soft_drinks",
            "empty_stomach_pain",
            "day1_ph_before",
            "day1_ph_after",
            "day2_ph_before",
            "day2_ph_after",
            "probability",
            "risk_level",
            "swbra_status",
            "swbra_score",
            "app_version",
            "model_version",
            "preprocessing_version",
            "swbra_spec_version",
            "created_at",
        )

        /** RFC 4180: quote when the value contains a comma, quote or newline. */
        internal fun escapeCsv(value: String): String {
            val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
            if (!needsQuotes) return value
            return "\"${value.replace("\"", "\"\"")}\""
        }
    }
}
