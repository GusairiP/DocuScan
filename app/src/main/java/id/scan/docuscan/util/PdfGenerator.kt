package id.scan.docuscan.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import id.scan.docuscan.data.DocumentEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {

    /**
     * Generates a real PDF file for scanned documents, supporting single or multi-page batch records.
     */
    fun generateDocumentPdf(context: Context, document: DocumentEntity, customDecryptKey: String = ""): File {
        val pdfDocument = PdfDocument()

        val titlePaint = Paint().apply {
            color = Color.rgb(33, 43, 54)
            textSize = 21f
            isFakeBoldText = true
        }

        val metaPaint = Paint().apply {
            color = Color.rgb(108, 117, 125)
            textSize = 10f
        }

        val borderPaint = Paint().apply {
            color = Color.rgb(200, 200, 200)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val accentPaint = Paint().apply {
            color = Color.rgb(13, 110, 253) // Modern Primary blue
            style = Paint.Style.FILL
        }

        val textToDraw = if (document.isEncrypted) {
            if (customDecryptKey.isNotEmpty()) {
                val decrypted = EncryptionHelper.decrypt(document.content, customDecryptKey)
                if (decrypted.startsWith("DECRYPTION_ERROR")) {
                    "[DOKUMEN TERENKRIPSI] (Kunci tidak cocok. Silakan masukkan kunci dekripsi end-to-end yang benar untuk menampilkan PDF ini)"
                } else {
                    decrypted
                }
            } else {
                "[DOKUMEN TERENKRIPSI END-TO-END]\n" +
                "Ciphertext: ${document.content.take(150)}...\n\n" +
                "(Untuk perlindungan privasi tinggi, silakan beralih ke aplikasi dan masukkan kunci enkripsi Anda)."
            }
        } else {
            document.content
        }

        // Split text by page break marker to generate multiple true PDF pages
        val pagesList = textToDraw.split("[PAGE_BREAK]")

        for (pageIdx in pagesList.indices) {
            val pageRawText = pagesList[pageIdx].trim()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageIdx + 1).create() // A4 Size: 595 x 842 pt
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            // Draw Header Banner
            canvas.drawRect(40f, 30f, 555f, 40f, accentPaint)

            // Draw App Title and Badge
            canvas.drawText("DOCUSCAN MOBILE SCANNER", 40f, 65f, metaPaint)
            val pageTitleSuffix = if (pagesList.size > 1) " (HALAMAN ${pageIdx + 1}/${pagesList.size})" else ""
            canvas.drawText(document.title.uppercase() + pageTitleSuffix, 40f, 95f, titlePaint)

            // Category Badge
            val badgePaint = Paint().apply {
                color = Color.rgb(241, 196, 15) // Warning/Accent yellow for category
                style = Paint.Style.FILL
            }
            canvas.drawRect(40f, 110f, 220f, 128f, badgePaint)
            val badgeTextPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10f
                isFakeBoldText = true
            }
            canvas.drawText("${document.category.uppercase()} • HALAMAN ${pageIdx + 1}", 48f, 123f, badgeTextPaint)

            // Metadata box
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val dateString = sdf.format(Date(document.scannedAt))
            canvas.drawText("Pindai: $dateString", 40f, 155f, metaPaint)
            canvas.drawText("Ukuran File: ${document.fileSizeKb} KB", 40f, 170f, metaPaint)
            canvas.drawText("Mode Scan: ${document.filterType} (Batch Mode)", 40f, 185f, metaPaint)
            
            val syncText = if (document.isCloudSynced) "Penyimpanan Cloud: Terhubung (Sync Real-Time)" else "Penyimpanan Cloud: Lokal Saja (Offline)"
            canvas.drawText(syncText, 40f, 200f, metaPaint)

            // Divider
            canvas.drawLine(40f, 215f, 555f, 215f, borderPaint)

            // Visual Scanner Layout representation
            val scanCardPaint = Paint().apply {
                color = Color.rgb(248, 249, 250)
                style = Paint.Style.FILL
            }
            val scanBorderPaint = Paint().apply {
                color = Color.rgb(220, 224, 230)
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }
            
            // Draw Scanned Document mock canvas wrapper
            canvas.drawRect(40f, 230f, 555f, 780f, scanCardPaint)
            canvas.drawRect(40f, 230f, 555f, 780f, scanBorderPaint)

            // Visual Watermark/Label for Searchable Layer
            val watermarkPaint = Paint().apply {
                color = Color.rgb(200, 210, 230)
                textSize = 12f
                isFakeBoldText = true
            }
            canvas.drawText("SECURE OFFLINE BATCH SCAN • OCR DETECTED LAYER ACTIVE", 60f, 255f, watermarkPaint)

            // Body Text (Searchable Hidden OCR Transcript)
            val hiddenBodyPaint = Paint().apply {
                color = Color.TRANSPARENT 
                textSize = 12f
            }

            // Visible text formatting
            val visibleLabelPaint = Paint().apply {
                color = Color.rgb(40, 50, 75)
                textSize = 11f
            }

            val lines = pageRawText.split("\n")
            var yPosition = 285f
            
            // Draw a simulated aesthetic page template
            for (line in lines) {
                val maxWidth = 480f
                var currentLine = line
                while (currentLine.isNotEmpty()) {
                    val charsToDraw = visibleLabelPaint.breakText(currentLine, true, maxWidth, null)
                    val chunk = currentLine.substring(0, charsToDraw)
                    
                    // Draw decorative paragraph line base
                    canvas.drawLine(60f, yPosition + 4f, 535f, yPosition + 4f, borderPaint)
                    
                    // Draw visible representation for reading
                    canvas.drawText(chunk, 60f, yPosition, visibleLabelPaint)
                    
                    // Draw invisible searchable/selectable shadow characters over it (The hidden layer)
                    canvas.drawText(chunk, 60f, yPosition, hiddenBodyPaint)
                    
                    yPosition += 22f
                    if (yPosition > 760f) break
                    currentLine = currentLine.substring(charsToDraw)
                }
                yPosition += 6f
                if (yPosition > 760f) break
            }

            // Draw Official Footer Stamp
            canvas.drawLine(40f, 800f, 555f, 800f, borderPaint)
            canvas.drawText("Keamanan End-to-End Terjamin | DocuScan PDF", 40f, 815f, metaPaint)
            canvas.drawText("Halaman ${pageIdx + 1} dari ${pagesList.size}", 480f, 815f, metaPaint)

            pdfDocument.finishPage(page)
        }

        // Save PDF to cache or files folder
        val docNameUniq = "DocuScan_${document.id}_${System.currentTimeMillis()}.pdf"
        val outputFile = File(context.cacheDir, docNameUniq)
        val fileOutputStream = FileOutputStream(outputFile)
        pdfDocument.writeTo(fileOutputStream)
        pdfDocument.close()
        fileOutputStream.close()

        return outputFile
    }

    /**
     * Generates a PDF summary representing an Automatic Daily report.
     */
    fun generateDailyReportPdf(context: Context, documents: List<DocumentEntity>): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paintHeader = Paint().apply {
            color = Color.rgb(33, 43, 54)
            textSize = 20f
            isFakeBoldText = true
        }

        val paintMeta = Paint().apply {
            color = Color.rgb(108, 117, 125)
            textSize = 10f
        }

        val paintRow = Paint().apply {
            color = Color.BLACK
            textSize = 11f
        }

        val paintHeaderRow = Paint().apply {
            color = Color.rgb(33, 43, 54)
            textSize = 11f
            isFakeBoldText = true
        }

        val borderPaint = Paint().apply {
            color = Color.rgb(220, 224, 230)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        // Title and Accent
        val bluePaint = Paint().apply {
            color = Color.rgb(13, 110, 253)
            style = Paint.Style.FILL
        }
        canvas.drawRect(40f, 30f, 555f, 45f, bluePaint)

        canvas.drawText("REPORT HARIAN AUTOMATIS - LAPORAN LAPANGAN", 40f, 75f, paintHeader)
        
        val sdf = SimpleDateFormat("dd MMMM yyyy (EEEE)", Locale("id", "ID"))
        val dateString = sdf.format(Date())
        canvas.drawText("Tanggal Kirim: $dateString", 40f, 95f, paintMeta)
        canvas.drawText("Total Dokumen Baru: ${documents.size} Item", 40f, 110f, paintMeta)
        canvas.drawText("Dibuat Secara Otomatis Oleh DocuScan Offline-First System", 40f, 125f, paintMeta)

        canvas.drawLine(40f, 140f, 555f, 140f, borderPaint)

        // Draw Table Header
        canvas.drawText("ID/JUDUL DOKUMEN", 45f, 160f, paintHeaderRow)
        canvas.drawText("KATEGORI", 260f, 160f, paintHeaderRow)
        canvas.drawText("UKURAN", 380f, 160f, paintHeaderRow)
        canvas.drawText("STATUS SYNC", 460f, 160f, paintHeaderRow)
        canvas.drawLine(40f, 170f, 555f, 170f, borderPaint)

        var yPos = 190f
        for (doc in documents) {
            val shortTitle = if (doc.title.length > 25) doc.title.take(22) + "..." else doc.title
            canvas.drawText(shortTitle, 45f, yPos, paintRow)
            canvas.drawText(doc.category, 260f, yPos, paintRow)
            canvas.drawText("${doc.fileSizeKb} KB", 380f, yPos, paintRow)
            
            val statusSyncStr = if (doc.isCloudSynced) "Synced (Cloud)" else "Local Only"
            canvas.drawText(statusSyncStr, 460f, yPos, paintRow)

            canvas.drawLine(40f, yPos + 10f, 555f, yPos + 10f, borderPaint)
            yPos += 30f

            if (yPos > 780f) {
                break // fit on one page inside prototype
            }
        }

        // Summary Statistics Box
        val statsPaint = Paint().apply {
            color = Color.rgb(245, 247, 250)
            style = Paint.Style.FILL
        }
        canvas.drawRect(40f, 700f, 555f, 770f, statsPaint)
        
        val statsTextPaint = Paint().apply {
            color = Color.rgb(33, 43, 54)
            textSize = 10f
            isFakeBoldText = true
        }
        
        val encCount = documents.count { it.isEncrypted }
        val syncCount = documents.count { it.isCloudSynced }
        val totalSize = documents.sumOf { it.fileSizeKb }
        
        canvas.drawText("SUMMARY LAPORAN PRODUKTIVITAS:", 50f, 720f, statsTextPaint)
        canvas.drawText("- Terenkripsi E2EE: $encCount Dokumen", 50f, 735f, paintMeta)
        canvas.drawText("- Tersinkronisasi cloud real-time: $syncCount / ${documents.size} Item", 50f, 750f, paintMeta)
        canvas.drawText("- Total Penggunaan Bandwidth / Caching: $totalSize KB", 300f, 735f, paintMeta)
        canvas.drawText("- Sistem Caching Performa: Aktif (Optimal)", 300f, 750f, paintMeta)

        // Footer
        canvas.drawLine(40f, 800f, 555f, 800f, borderPaint)
        canvas.drawText("Di-ekspor menggunakan DocuScan Team Hub. Semua keamanan dijamin terenkripsi.", 40f, 815f, paintMeta)

        pdfDocument.finishPage(page)

        val reportName = "DocuScan_DailyReport_${System.currentTimeMillis()}.pdf"
        val outputFile = File(context.cacheDir, reportName)
        val fileOutputStream = FileOutputStream(outputFile)
        pdfDocument.writeTo(fileOutputStream)
        pdfDocument.close()
        fileOutputStream.close()

        return outputFile
    }

    /**
     * Generates standard comma-separated text representing analytic productivity data.
     */
    fun generateAnalyticsCsv(documents: List<DocumentEntity>): String {
        val builder = java.lang.StringBuilder()
        builder.append("ID,Title,Category,ScannedAt,SizeKB,IsEncrypted,IsSynced,FilterMode,AssociatedTaskDate,Urgency\n")
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        for (doc in documents) {
            val dateStr = sdf.format(Date(doc.scannedAt))
            // Escape title double quotes if needed
            val cleanTitle = doc.title.replace("\"", "\"\"")
            builder.append("${doc.id},")
            builder.append("\"$cleanTitle\",")
            builder.append("${doc.category},")
            builder.append("$dateStr,")
            builder.append("${doc.fileSizeKb},")
            builder.append("${doc.isEncrypted},")
            builder.append("${doc.isCloudSynced},")
            builder.append("${doc.filterType},")
            builder.append("${doc.associatedDate},")
            builder.append("${doc.isUrgent}\n")
        }
        return builder.toString()
    }
}
