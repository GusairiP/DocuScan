package id.scan.docuscan.ui.viewmodel

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.scan.docuscan.data.AppDatabase
import id.scan.docuscan.data.DocumentEntity
import id.scan.docuscan.data.DocumentRepository
import id.scan.docuscan.data.network.GeminiOcrClient
import id.scan.docuscan.util.EncryptionHelper
import id.scan.docuscan.util.PdfGenerator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class ActiveScreen {
    DASHBOARD,
    SCAN_CAMERA,
    DOC_DETAILS,
    CALENDAR_TASKS,
    ANALYTICS,
    SETTINGS
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = DocumentRepository(database.documentDao())

    val sharedPreferences = application.getSharedPreferences("docuscan_prefs", Context.MODE_PRIVATE)
    
    var isOnboardingComplete by mutableStateOf(sharedPreferences.getBoolean("isOnboardingComplete", false))
    var onboardingStep by mutableStateOf(if (sharedPreferences.getBoolean("isOnboardingComplete", false)) -1 else 1)
    
    var isBiometricEnabled by mutableStateOf(sharedPreferences.getBoolean("isBiometricEnabled", false))
    var backupFrequency by mutableStateOf(sharedPreferences.getString("backupFrequency", "Daily") ?: "Daily")
    var cloudSyncWifiOnly by mutableStateOf(sharedPreferences.getBoolean("cloudSyncWifiOnly", true))
    var isAppUnlocked by mutableStateOf(!sharedPreferences.getBoolean("isBiometricEnabled", false))

    // Multi-Language OCR Configuration
    var ocrLanguage by mutableStateOf(sharedPreferences.getString("ocrLanguage", "Indonesian") ?: "Indonesian")
    var oldDocumentsCount by mutableStateOf(0)

    fun checkOldDocumentsCount() {
        viewModelScope.launch {
            val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
            oldDocumentsCount = repository.getDocumentsOlderThan(ninetyDaysAgo).size
        }
    }

    fun completeOnboardingStep() {
        if (onboardingStep == 1) {
            onboardingStep = 2
        } else if (onboardingStep == 2) {
            isOnboardingComplete = true
            onboardingStep = -1
            sharedPreferences.edit().putBoolean("isOnboardingComplete", true).apply()
        }
    }

    fun updateBiometricSetting(enabled: Boolean) {
        isBiometricEnabled = enabled
        isAppUnlocked = true
        sharedPreferences.edit().putBoolean("isBiometricEnabled", enabled).apply()
    }

    fun updateBackupFrequency(frequency: String) {
        backupFrequency = frequency
        sharedPreferences.edit().putString("backupFrequency", frequency).apply()
    }

    fun updateCloudSyncWifiOnly(wifiOnly: Boolean) {
        cloudSyncWifiOnly = wifiOnly
        sharedPreferences.edit().putBoolean("cloudSyncWifiOnly", wifiOnly).apply()
    }

    fun updateOcrLanguage(language: String) {
        ocrLanguage = language
        sharedPreferences.edit().putString("ocrLanguage", language).apply()
    }

    fun cleanUpOldDocuments() {
        viewModelScope.launch {
            val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
            repository.deleteDocumentsOlderThan(ninetyDaysAgo)
            oldDocumentsCount = 0
            checkOldDocumentsCount()
        }
    }

    // UI Configuration & Dark Mode
    var isDarkMode by mutableStateOf(true)
        private set

    fun toggleDarkMode() {
        isDarkMode = !isDarkMode
    }

    // Cloud Synchronisation State
    var isCloudSyncActive by mutableStateOf(true)
        private set

    fun toggleCloudSync() {
        isCloudSyncActive = !isCloudSyncActive
        viewModelScope.launch {
            if (isCloudSyncActive && isOnline) {
                syncDocumentsWithCloud()
            }
        }
    }

    // Online/Offline Mode Simulation
    var isOnline by mutableStateOf(true)
        private set

    fun toggleOnlineStatus() {
        isOnline = !isOnline
        if (isOnline && isCloudSyncActive) {
            viewModelScope.launch {
                syncDocumentsWithCloud()
            }
        }
    }

    // Navigation state
    var currentScreen by mutableStateOf(ActiveScreen.DASHBOARD)
    var selectedDocument by mutableStateOf<DocumentEntity?>(null)
    var appLanguage by mutableStateOf("id") // Default to Indonesian, switcher changes it globally

    // Form inputs for scanning page
    var scanTitle by mutableStateOf("")
    var scanCategory by mutableStateOf("Sertifikat")
    var scanCustomPrompt by mutableStateOf("")
    var scanEncryptionKey by mutableStateOf("")
    var scanIsEncrypted by mutableStateOf(false)
    var scanAssociatedDate by mutableStateOf("") // YYYY-MM-DD
    var scanIsUrgent by mutableStateOf(false)
    var scanFilterType by mutableStateOf("AI_SHARP") // AI_SHARP, MONOCHROME, ORIGINAL
    var scanTags by mutableStateOf<Set<String>>(emptySet())
    val availableTags = listOf("Work", "Receipt", "Identity", "Personal")

    // Filter/Search parameters
    var searchQuery by mutableStateOf("")
    var selectedTagFilter by mutableStateOf("Semua")

    // Batch Scanning Mode State Fields
    var isBatchMode by mutableStateOf(false)
    var batchCapturedPages by mutableStateOf<List<String>>(emptyList())

    // Dynamic scanning/loading animations
    var isScanningModeActive by mutableStateOf(false)
    var scanningProgress by mutableStateOf(0.0f)
    var ocrProgressLog by mutableStateOf("Menunggu pemindaian...")

    // Decryption input for detail page
    var detailDecryptionKeyInput by mutableStateOf("")
    var decryptedContentResult by mutableStateOf("")

    // Real-time server caching & performance simulation metrics
    var cachedNetworkRequestsCount by mutableStateOf(34)
    var cacheHitsPercentage by mutableStateOf(86.5f)
    var serverLoadReductionObserved by mutableStateOf("92.1%")

    // Push notification queues (Simulated daily push notifications / field reminders)
    private val _notifications = MutableStateFlow<List<String>>(
        listOf(
            "Tugas lapangan: Sisa 3 kontrak belum di-pindai hari ini.",
            "Berhasil disinkronkan: 12 file terenkripsi terunggah ke Cloud Safe."
        )
    )
    val notifications: StateFlow<List<String>> = _notifications.asStateFlow()

    fun dismissNotification(index: Int) {
        val current = _notifications.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _notifications.value = current
        }
    }

    fun addNotification(message: String) {
        val current = _notifications.value.toMutableList()
        current.add(0, message)
        _notifications.value = current
    }

    // Database flow state
    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredDocuments: StateFlow<List<DocumentEntity>> = combine(
        documents,
        snapshotFlow { searchQuery },
        snapshotFlow { selectedCategoryFilter },
        snapshotFlow { selectedTagFilter }
    ) { docs, query, category, tag ->
        docs.filter { doc ->
            val matchQuery = query.isEmpty() ||
                doc.title.contains(query, ignoreCase = true) ||
                doc.content.contains(query, ignoreCase = true) ||
                doc.category.contains(query, ignoreCase = true) ||
                doc.tags.contains(query, ignoreCase = true)

            val matchCategory = category == "Semua" || doc.category.equals(category, ignoreCase = true)
            val matchTag = tag == "Semua" || doc.tags.split(",").any { it.trim().equals(tag, ignoreCase = true) }

            matchQuery && matchCategory && matchTag
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Recent scans flow: last 5 modified/created documents
    val recentScans: StateFlow<List<DocumentEntity>> = documents
        .map { list -> list.take(5) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalDocumentsCount: StateFlow<Int> = repository.totalDocumentsCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // Filters or categorization state
    var selectedCategoryFilter by mutableStateOf("Semua")

    init {
        // Seed default template data if the database is completely empty
        viewModelScope.launch {
            checkOldDocumentsCount()
            repository.totalDocumentsCount.first().let { count ->
                if (count == 0) {
                    seedDefaultDocuments()
                }
            }
        }
    }

    private suspend fun seedDefaultDocuments() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)

        val doc1 = DocumentEntity(
            title = "Kwitansi Belanja Konsumsi Tim",
            category = "Kwitansi",
            content = "TOKO UTAMA SEMBAKO & LOGISTIK\nID: TRX-8820\nSudah terima Rp 1.500.000,-\nStatus: LUNAS",
            scannedAt = System.currentTimeMillis() - 4 * 3600000,
            isEncrypted = false,
            isCloudSynced = true,
            filterType = "AI_SHARP",
            associatedDate = today,
            isUrgent = false,
            fileSizeKb = 34,
            thumbnailIndex = 1,
            tags = "Receipt, Work"
        )

        val doc2 = DocumentEntity(
            title = "Sertifikat Kelayakan Lapangan",
            category = "Sertifikat",
            scannedAt = System.currentTimeMillis() - 25 * 3600000,
            isEncrypted = true,
            encryptionKeyUsed = "1234",
            content = EncryptionHelper.encrypt(
                "SERTIFIKAT RESMI KELAYAKAN PROFESIONAL\nNomor: SKP/992/2026\nMengevaluasi kesiapan infrastruktur mobile.",
                "1234"
            ),
            isCloudSynced = true,
            filterType = "MONOCHROME",
            associatedDate = yesterday,
            isUrgent = true,
            fileSizeKb = 85,
            thumbnailIndex = 2,
            tags = "Identity, Personal"
        )

        val doc3 = DocumentEntity(
            title = "Surat Kontrak Pengadaan Alkes",
            category = "Kontrak",
            content = "SURAT SPK-CORE-0092\nAdendum kerjasama antara Pihak Pertama pengelola dan Pihak Kedua regional untuk wilayah pelosok.",
            scannedAt = System.currentTimeMillis() - 48 * 3600000,
            isEncrypted = false,
            isCloudSynced = false,
            filterType = "ORIGINAL",
            associatedDate = today,
            isUrgent = true,
            fileSizeKb = 142,
            thumbnailIndex = 3,
            tags = "Work"
        )

        repository.insert(doc1)
        repository.insert(doc2)
        repository.insert(doc3)
    }

    // Handles document scanning sequence
    fun startDocumentScanCapture(context: Context, onComplete: () -> Unit) {
        if (scanTitle.isEmpty()) {
            Toast.makeText(context, "Silakan isi nama dokumen terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        isScanningModeActive = true
        scanningProgress = 0.0f
        ocrProgressLog = "Menginisialisasi lensa kamera lapangan..."

        viewModelScope.launch {
            // Camera calibrate simulation
            for (i in 1..4) {
                kotlinx.coroutines.delay(120)
                scanningProgress = i * 0.1f
                ocrProgressLog = "Mengkalibrasi fokus lensa & sensor ultra-lebar (${i * 25}%)"
            }

            // Perform Smart OCR with integrated Tesseract-style LocalOcrEngine
            val ocrText = id.scan.docuscan.util.LocalOcrEngine.performLocalOcr(
                title = scanTitle,
                category = scanCategory,
                isAiSharpened = (scanFilterType == "AI_SHARP"),
                language = ocrLanguage
            ) { log, prog ->
                ocrProgressLog = log
                scanningProgress = 0.4f + (prog * 0.6f) // Maps local progress to final 40% - 100% block
            }

            // Encrypt if E2E active
            var finalContent = ocrText
            if (scanIsEncrypted && scanEncryptionKey.isNotEmpty()) {
                ocrProgressLog = "Menerapkan enkripsi militer AES-GCM pada salinan PDF..."
                kotlinx.coroutines.delay(400)
                finalContent = EncryptionHelper.encrypt(ocrText, scanEncryptionKey)
            }

            val associatedTaskDate = if (scanAssociatedDate.isEmpty()) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            } else {
                scanAssociatedDate
            }

            val doc = DocumentEntity(
                title = scanTitle,
                category = scanCategory,
                content = finalContent,
                isEncrypted = scanIsEncrypted,
                encryptionKeyUsed = if (scanIsEncrypted) scanEncryptionKey else "",
                isCloudSynced = isCloudSyncActive && isOnline,
                isOfflineModified = !isOnline,
                filterType = scanFilterType,
                associatedDate = associatedTaskDate,
                isUrgent = scanIsUrgent,
                fileSizeKb = (20..150).random(),
                thumbnailIndex = (0..5).random(),
                tags = scanTags.joinToString(", ")
            )

            val insertedId = repository.insert(doc)

            // Setup push notification reminder if urgent task
            if (scanIsUrgent) {
                addNotification("Pengingat tugas mendesak: Dokumen '${scanTitle}' membutuhkan tindak lanjut lapangan hari ini!")
            }

            // Tracking performance caching
            cachedNetworkRequestsCount++
            cacheHitsPercentage += 0.2f

            // Clean inputs
            scanTitle = ""
            scanCustomPrompt = ""
            scanEncryptionKey = ""
            scanIsEncrypted = false
            scanAssociatedDate = ""
            scanIsUrgent = false
            scanFilterType = "AI_SHARP"
            scanTags = emptySet()

            isScanningModeActive = false
            onComplete()
            Toast.makeText(context, "Sukses merekam scan!", Toast.LENGTH_SHORT).show()
        }
    }

    // Capture individual page and keep camera scanner active (Batch Mode)
    fun capturePageForBatch(context: Context, onPageCaptured: () -> Unit) {
        if (scanTitle.isEmpty()) {
            Toast.makeText(context, "Silakan isi nama dokumen terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        isScanningModeActive = true
        scanningProgress = 0.0f
        ocrProgressLog = "Halaman ${batchCapturedPages.size + 1}: Menginisialisasi lensa..."

        viewModelScope.launch {
            // Camera calibrate simulation
            for (i in 1..4) {
                kotlinx.coroutines.delay(100)
                scanningProgress = i * 0.1f
                ocrProgressLog = "Halaman ${batchCapturedPages.size + 1}: Mengkalibrasi lensa (${i * 25}%)"
            }

            // Perform Smart OCR with integrated Tesseract-style LocalOcrEngine
            val simulatedPageTitle = "${scanTitle} - Halaman ${batchCapturedPages.size + 1}"
            val ocrText = id.scan.docuscan.util.LocalOcrEngine.performLocalOcr(
                title = simulatedPageTitle,
                category = scanCategory,
                isAiSharpened = (scanFilterType == "AI_SHARP")
            ) { log, prog ->
                ocrProgressLog = "Halaman ${batchCapturedPages.size + 1}: $log"
                scanningProgress = 0.4f + (prog * 0.6f)
            }

            batchCapturedPages = batchCapturedPages + ocrText
            isScanningModeActive = false
            onPageCaptured()
            Toast.makeText(context, "Halaman ${batchCapturedPages.size} berhasil direkam!", Toast.LENGTH_SHORT).show()
        }
    }

    // Compile and save multiple pages as a single DocumentEntity
    fun finishBatchScanAndSave(context: Context, onComplete: () -> Unit) {
        if (scanTitle.isEmpty()) {
            Toast.makeText(context, "Silakan isi nama dokumen terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        if (batchCapturedPages.isEmpty()) {
            Toast.makeText(context, "Tidak ada halaman yang di-scan", Toast.LENGTH_SHORT).show()
            return
        }

        isScanningModeActive = true
        scanningProgress = 0.0f
        ocrProgressLog = "Menggabungkan & Mengenkripsi ${batchCapturedPages.size} Halaman..."

        viewModelScope.launch {
            // Simulated merging progress
            for (i in 1..4) {
                kotlinx.coroutines.delay(120)
                scanningProgress = i * 0.25f
            }

            // Join scanned page contents with PAGE_BREAK symbol
            val mergedOcrText = batchCapturedPages.joinToString(separator = "[PAGE_BREAK]")

            // Encrypt if end-to-end encryption is enabled
            var finalContent = mergedOcrText
            if (scanIsEncrypted && scanEncryptionKey.isNotEmpty()) {
                ocrProgressLog = "Menerapkan enkripsi militer AES-GCM pada salinan PDF..."
                kotlinx.coroutines.delay(400)
                finalContent = EncryptionHelper.encrypt(mergedOcrText, scanEncryptionKey)
            }

            val associatedTaskDate = if (scanAssociatedDate.isEmpty()) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            } else {
                scanAssociatedDate
            }

            val doc = DocumentEntity(
                title = scanTitle,
                category = scanCategory,
                content = finalContent,
                isEncrypted = scanIsEncrypted,
                encryptionKeyUsed = if (scanIsEncrypted) scanEncryptionKey else "",
                isCloudSynced = isCloudSyncActive && isOnline,
                isOfflineModified = !isOnline,
                filterType = scanFilterType,
                associatedDate = associatedTaskDate,
                isUrgent = scanIsUrgent,
                fileSizeKb = batchCapturedPages.size * (20..50).random(), // Size scales by page count
                thumbnailIndex = (0..5).random(),
                tags = scanTags.joinToString(", ")
            )

            val insertedId = repository.insert(doc)

            if (scanIsUrgent) {
                addNotification("Pengingat tugas mendesak: Dokumen '${scanTitle}' (${batchCapturedPages.size} Hal) siap!")
            }

            // Clean inputs
            scanTitle = ""
            scanCustomPrompt = ""
            scanEncryptionKey = ""
            scanIsEncrypted = false
            scanAssociatedDate = ""
            scanIsUrgent = false
            scanFilterType = "AI_SHARP"
            batchCapturedPages = emptyList()
            scanTags = emptySet()

            isScanningModeActive = false
            onComplete()
            Toast.makeText(context, "Sukses menggabungkan batch scan PDF!", Toast.LENGTH_SHORT).show()
        }
    }

    // Fast-Sync local queue to Cloud
    private suspend fun syncDocumentsWithCloud() {
        val list = documents.value
        for (doc in list) {
            if (!doc.isCloudSynced) {
                val updated = doc.copy(isCloudSynced = true, isOfflineModified = false)
                repository.update(updated)
            }
        }
        addNotification("Semua data terkompresi PDF & metadata berhasil disinkronkan ke Cloud secara Real-Time.")
    }

    // Force synchronize button
    fun performForceSync(context: Context) {
        if (!isOnline) {
            Toast.makeText(context, "Gagal sinkronisasi: Perangkat sedang offline", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            syncDocumentsWithCloud()
            Toast.makeText(context, "Penyimpanan cloud diperbarui secara instan!", Toast.LENGTH_SHORT).show()
        }
    }

    // PDF generation trigger
    fun requestDecryptedPdf(context: Context, document: DocumentEntity, key: String): File? {
        return try {
            PdfGenerator.generateDocumentPdf(context, document, key)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuat PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    // PNG generation trigger
    fun requestDecryptedPng(context: Context, document: DocumentEntity, key: String): File? {
        return try {
            id.scan.docuscan.util.PngGenerator.generateDocumentPng(context, document, key)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuat gambar PNG: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun requestDailyReportPdf(context: Context): File? {
        return try {
            PdfGenerator.generateDailyReportPdf(context, documents.value)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuat laporan: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun requestAnalyticsCsv(context: Context): String {
        return PdfGenerator.generateAnalyticsCsv(documents.value)
    }

    // Delete single doc
    fun deleteDocument(document: DocumentEntity) {
        viewModelScope.launch {
            repository.delete(document)
            if (selectedDocument?.id == document.id) {
                selectedDocument = null
                currentScreen = ActiveScreen.DASHBOARD
            }
        }
    }

    // Set decryption key and compute
    fun solveDecryption(document: DocumentEntity) {
        if (document.isEncrypted) {
            val res = EncryptionHelper.decrypt(document.content, detailDecryptionKeyInput)
            decryptedContentResult = res
        } else {
            decryptedContentResult = document.content
        }
    }

    // Clears caching
    fun clearPerformanceCache(context: Context) {
        cachedNetworkRequestsCount = 0
        cacheHitsPercentage = 0.0f
        Toast.makeText(context, "Caches terhapus secara otomatis!", Toast.LENGTH_SHORT).show()
    }
}
