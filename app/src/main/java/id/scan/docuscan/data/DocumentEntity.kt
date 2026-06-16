package id.scan.docuscan.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String, // OCR Detected Text
    val scannedAt: Long = System.currentTimeMillis(),
    val category: String = "Sertifikat", // Receipt, Invoice, Contract, Personal, etc.
    val isEncrypted: Boolean = false,
    val encryptionKeyUsed: String = "",
    val isCloudSynced: Boolean = false,
    val isOfflineModified: Boolean = false,
    val filterType: String = "AI_SHARP", // AI_SHARP, MONOCHROME, ORIGINAL
    val associatedDate: String = "", // YYYY-MM-DD for tasks/calendar
    val isUrgent: Boolean = false,
    val fileSizeKb: Int = 42,
    val thumbnailIndex: Int = 0, // Determines which abstract visual layout is rendered
    val tags: String = "" // Comma-separated tags (e.g., 'Work', 'Receipt', 'Identity')
) : Serializable
