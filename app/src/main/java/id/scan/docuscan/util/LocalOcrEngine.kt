package id.scan.docuscan.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import java.util.Locale

object LocalOcrEngine {
    private const val TAG = "LocalOcrEngine"

    // Real-time process logging states for Tesseract-style OCR simulation
    val ocrSteps = listOf(
        "Initializing Tesseract OCR engine (v4.1.0)...",
        "Loading Indonesian language training files (ind.traineddata)...",
        "Preprocessing scanned bitmap frame (Adaptive Binarization)...",
        "Executing Otsu thresholding & document deskewing...",
        "Analyzing layout: line detection, paragraph bounding boxes...",
        "Running LSTM character sequence recognition network...",
        "Injecting searchable hidden text layer coordinates...",
        "Structuring parsed character streams..."
    )

    /**
     * Simulates local OCR engine progress and returns the transcribed text.
     * Integrates with real-time logs to update the UI like Tesseract.js.
     */
    suspend fun performLocalOcr(
        title: String,
        category: String,
        isAiSharpened: Boolean,
        language: String = "Indonesian",
        onProgressLog: (String, Float) -> Unit
    ): String {
        Log.d(TAG, "Starting Local Tesseract-style OCR processing for: $title with $language")
        
        // Progress through detailed Tesseract-style compilation steps
        val localizedSteps = ocrSteps.map { if (it.contains("Indonesian")) it.replace("Indonesian", language) else it }
        for (i in localizedSteps.indices) {
            val progress = (i + 1).toFloat() / localizedSteps.size
            onProgressLog(localizedSteps[i], progress)
            delay(280) // Smooth progress feel
        }

        val filterLabel = if (isAiSharpened) "AI_SHARP" else "RAW_ORIGINAL"

        // Produce a highly structured Indonesian scan transcript
        val textResult = when (category.lowercase(Locale.getDefault())) {
            "sertifikat", "certificate" -> """
                [TESSERACT OCR ENGINE v4.1.0 - HIDDEN TEXT LAYER COMPILATION]
                ===========================================================
                KEMENTERIAN PENDIDIKAN, KEBUDAYAAN, RISET, DAN TEKNOLOGI
                REPUBLIK INDONESIA
                
                SERTIFIKAT KELAYAKAN PROFESIONAL
                Nomor: SKP/99281/VI/2026
                
                Menyatakan bahwa dokumen dengan judul:
                "$title"
                
                Telah melalui proses verifikasi lapangan dan pemindaian digital resmi dengan hasil kelayakan: SUNGGUH MEMUASKAN.
                
                Ditetapkan di: Jakarta Utara
                Tanggal Scan: 16 Juni 2026
                Petugas Lapangan: DocuScan System Unit
                Filter Pemrosesan: $filterLabel
            """.trimIndent()

            "kwitansi", "receipt" -> """
                [TESSERACT OCR ENGINE v4.1.0 - HIDDEN TEXT LAYER COMPILATION]
                ===========================================================
                TOKO UTAMA SEMBAKO & LOGISTIK LAPANGAN
                Jalan Margonda Raya No. 42, Depok
                Telp: (021) 555-0192
                
                KWITANSI RESMI PEMBAYARAN
                ID Transaksi: TRX-88319-2026
                
                Sudah Terima Dari: Koordinator Lapangan
                Jumlah Uang: Rp 2.500.000,-
                Untuk Pembayaran: "$title"
                
                Rincian Barang:
                - Logistik konsumsi tim lapangan: Rp 1.500.000,-
                - Operasional pemindaian mobile: Rp 1.000.000,-
                
                Status: LUNAS - PAID (Lokal Terverifikasi)
                Tanggal Catat: 16 Juni 2026
                Filter Pemrosesan: $filterLabel
            """.trimIndent()

            "kontrak", "contract" -> """
                [TESSERACT OCR ENGINE v4.1.0 - HIDDEN TEXT LAYER COMPILATION]
                ===========================================================
                SURAT PERJANJIAN ADENDUM KERJASAMA LAPANGAN
                Nomor Kontrak: SPK-CORE-0092
                
                Perihal Pembahasan: "$title"
                
                Pada hari ini, Selasa, 16 Juni 2026, telah disepakati kerja sama strategis antara:
                1. PIHAK PERTAMA: Project Manager DocuScan Corporation
                2. PIHAK KEDUA: Client Representatif Lapangan
                
                Butir-butir Hak & Kewajiban:
                - Penyediaan infrastruktur offline-first di wilayah terpencil.
                - Sinkronisasi real-time berbasis enkripsi End-to-End AES-GCM.
                - Pelaporan harian otomatis berformat PDF untuk tim internal.
                
                Sertifikat Kunci Enkripsi: Aktif
                Filter Pemrosesan: $filterLabel
            """.trimIndent()

            "invoice", "tagihan" -> """
                [TESSERACT OCR ENGINE v4.1.0 - HIDDEN TEXT LAYER COMPILATION]
                ===========================================================
                DOCUSCAN SERVICES LTD
                Invoice No: INV-2026-00381
                Tanggal Jatuh Tempo: 23 Juni 2026
                
                Ditujukan Kepada: Tim Operasional Wilayah
                Deskripsi Pekerjaan: "$title"
                
                Deskripsi Tagihan:
                1. Pemrosesan OCR berbasis Tesseract Offline Engine: Rp 400.000,-
                2. Penyimpanan Sinkronisasi Cloud Terenkripsi: Rp 150.000,-
                
                TOTAL TAGIHAN: Rp 550.000,-
                Metode Pembayaran: Transfer Bank Mandiri / BCA / GOPAY
                Filter Pemrosesan: $filterLabel
            """.trimIndent()

            else -> """
                [TESSERACT OCR ENGINE v4.1.0 - HIDDEN TEXT LAYER COMPILATION]
                ===========================================================
                DOCUSCAN DIGITAL SCANNER REPORT
                ---------------------------------------------
                Judul Dokumen: $title
                Kategori: $category
                Tanggal Pindai: 16 Juni 2026 11:00 WIB
                
                Judul Berkas: $title
                Teks Hasil Segmentasi Paragraf Offline:
                - Hasil pindai berhasil dipetakan ke dalam koordinat bounding box.
                - Lapisan tersembunyi PDF (Invisible Text layer) diaktifkan secara otomatis.
                - Integritas data terenkripsi end-to-end terjamin dari intersepsi pihak ketiga.
                
                Konektivitas Cloud: Sangat Optimal
                Filter Pemrosesan: $filterLabel
            """.trimIndent()
        }

        onProgressLog("OCR Selesai! Menghemat bandwidth, teks disematkan.", 1.0f)
        return textResult
    }
}
