package id.scan.docuscan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import id.scan.docuscan.data.DocumentEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PngGenerator {
    /**
     * Generates a fully formatted PNG visual layout representation of the scanned document on a canvas.
     */
    fun generateDocumentPng(context: Context, document: DocumentEntity, customDecryptKey: String = ""): File {
        // Create an A4-proportioned bitmap representation for the PNG scan output (800 x 1130 pixels)
        val width = 800
        val height = 1130
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw pristine white sheet background
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint().apply {
            color = Color.rgb(33, 43, 54)
            textSize = 26f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(108, 117, 125)
            textSize = 13f
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.rgb(230, 233, 238)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        val accentPaint = Paint().apply {
            color = Color.rgb(13, 110, 253) // Jetpack Primary Blue
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val textToDraw = if (document.isEncrypted) {
            if (customDecryptKey.isNotEmpty()) {
                val decrypted = EncryptionHelper.decrypt(document.content, customDecryptKey)
                if (decrypted.startsWith("DECRYPTION_ERROR")) {
                    "[DOKUMEN TERENKRIPSI] (Kunci tidak cocok. Masukkan kunci dekripsi AES-GCM yang benar untuk memuat teks)"
                } else {
                    decrypted
                }
            } else {
                "[DOKUMEN TERENKRIPSI END-TO-END]\nCiphertext: ${document.content.take(120)}...\n\n(Harap masukkan kunci dekripsi rahasia Anda pada layar pratinjau dokumen untuk menampilkan teks)"
            }
        } else {
            document.content
        }

        // Format [PAGE_BREAK] tags nicely for image visual layout
        val cleanedText = textToDraw.replace("[PAGE_BREAK]", "\n--- BATAS HALAMAN MULTI-BATCH ---\n")

        // Draw official Header Accent line
        canvas.drawRect(50f, 40f, 750f, 52f, accentPaint)

        // Draw header metadata
        canvas.drawText("DOCUSCAN FIELD SYSTEM • EKSPOR GAMBAR PNG OFF-LINE", 50f, 85f, metaPaint)
        canvas.drawText(document.title.uppercase(), 50f, 125f, titlePaint)

        // Draw Category and Tags badges side-by-side
        val badgePaint = Paint().apply {
            color = Color.rgb(241, 196, 15) // Warning Accent Yellow
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(50f, 145f, 250f, 172f, badgePaint)
        val badgeTextPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(document.category.uppercase(), 62f, 163f, badgeTextPaint)

        // Only draw tag badge if the document is tagged with something
        if (document.tags.isNotEmpty()) {
            val tagBadgePaint = Paint().apply {
                color = Color.rgb(40, 167, 69) // Success vibrant green
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRect(260f, 145f, 520f, 172f, tagBadgePaint)
            val tagTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 11f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText("TAGS: ${document.tags.uppercase()}", 272f, 163f, tagTextPaint)
        }

        // Draw descriptive metadata parameters
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(document.scannedAt))
        canvas.drawText("Tanggal Pindai: $dateStr", 50f, 205f, metaPaint)
        canvas.drawText("Detail Metadata: ${document.fileSizeKb} KB • Mode Filter: ${document.filterType}", 50f, 225f, metaPaint)

        val syncStatusStr = if (document.isCloudSynced) "Penyimpanan Cloud: Aktif Terhubung" else "Penyimpanan Cloud: Lokal Cadangan (Offline)"
        canvas.drawText(syncStatusStr, 50f, 245f, metaPaint)

        // Draw separating lines
        canvas.drawLine(50f, 265f, 750f, 265f, borderPaint)

        // Draw simulated scanner paper card layout
        val scanCardPaint = Paint().apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
        }
        val scanBorderPaint = Paint().apply {
            color = Color.rgb(218, 224, 233)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(50f, 285f, 750f, 1030f, scanCardPaint)
        canvas.drawRect(50f, 285f, 750f, 1030f, scanBorderPaint)

        // Write beautiful OCR text lines
        val textPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 13.5f
            isAntiAlias = true
        }

        val textLines = cleanedText.split("\n")
        var currentY = 325f

        for (line in textLines) {
            val maxDrawWidth = 635f
            var lineSegment = line
            while (lineSegment.isNotEmpty()) {
                val measuredChars = textPaint.breakText(lineSegment, true, maxDrawWidth, null)
                val chunkOfLine = lineSegment.substring(0, measuredChars)

                // Render micro horizontal grid base under line for premium scanner representation
                canvas.drawLine(70f, currentY + 5f, 730f, currentY + 5f, borderPaint)

                // Render text chunk
                canvas.drawText(chunkOfLine, 72f, currentY, textPaint)

                currentY += 28f
                if (currentY > 1000f) break
                lineSegment = lineSegment.substring(measuredChars)
            }
            currentY += 10f
            if (currentY > 1000f) break
        }

        // Draw Official Signature & Footer Area
        val footerBorderPaint = Paint().apply {
            color = Color.rgb(200, 205, 215)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawLine(50f, 1060f, 750f, 1060f, footerBorderPaint)
        canvas.drawText("Otisensi DocuScan Security Suite • Dokumen Sah Ekspor Mandiri", 50f, 1090f, metaPaint)

        // Write to Cache Folder securely
        val uniqDocName = "DocuScan_${document.id}_${System.currentTimeMillis()}.png"
        val cachePngFile = File(context.cacheDir, uniqDocName)
        val outStream = FileOutputStream(cachePngFile)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
        outStream.flush()
        outStream.close()

        return cachePngFile
    }
}
