# 🛡️ DocuScan: Premium Mobile Scanner & Field Secure Archiver

DocuScan is a production-ready, client-side offline-first Android application designed for heavy utility, field documentation, and high-privacy secure storage. Engineered with modern **Jetpack Compose**, **offline-first SQLite-Room mechanics**, high-performance **AES-256 local document encryption**, and **Google Gemini intelligence**, it enables lightning-fast local document digitization, intelligent structure categorizations, and continuous batch capture pipelines in the field.

---

## ✨ Features Suite

1. **Continuous Batch Scanning Mode**: Capture and queue multiple document pages continuously in real-time, sharpening borders with active auto-edge crop frames before compiling them into a unified multi-page document sheet.
2. **Intelligent OCR (Gemini API Integration)**: Instantly processes captured text snippets, extracts structural layers, and supports full-text search index generation.
3. **Local End-to-End Encryption (E2EE)**: Protect folders or individual documents with client-side AES-GCM 256-bit keys. Toggling encryption ensures local files and background backup payloads remain entirely secure.
4. **Advanced Multi-Metadata Searching**: A smart dashboard search bar filters document titles, categories, OCR content, and custom tags simultaneously, ensuring sub-second files location.
5. **Recent Scans Quick Panel**: A prominent horizontal carousel displays the last 5 modified items on the dashboard for instant, fluid resume actions.
6. **Granular Categorization & Custom Tags**: Group scans under official folders (`Sertifikat`, `Kwitansi`, `Kontrak`, `Invoice`, `Lainnya`) and organize files by key labels (`Work`, `Receipt`, `Identity`, `Personal`).
7. **Pristine Quick Share Triggers**:
   - **Native PDF Compilation**: Generate real, searchable A4-proportioned PDF records with background OCR layouts.
   - **Direct PNG Image Share**: Render the document's design layout directly to a high-resolution PNG bitmap canvas for instant picture sharing.
   - **Secure Expiring Link (Quick Web Share)**: Generate virtual sharing URLs containing cryptographic access tokens with an automated 2-hour virtual TTL.

---

## 🗄️ Database Architecture

The data ecosystem is controlled locally using an offline-first **Room SQLite DB** configuration. This prevents files or scans from being lost under hazardous field internet drops.

### Database Schema Table: `documents`

Below is the structured layout of the relational SQLite entities managed by `id.scan.docuscan.data.AppDatabase` under version **`2`**:

| Field Attribute | SQLite Type | Kotlin DataType | Keys / Indexes | Default Value | Functional Specification / Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | `INTEGER` | `Int` | `PRIMARY KEY (Auto-Gen)` | `0` | Unique relational auto-increment primary identifier. |
| `title` | `TEXT` | `String` | None | None | Custom document label defined by the operator or auto-named. |
| `content` | `TEXT` | `String` | None | None | Raw OCR textual extraction, or encrypted cipher strings when locked. |
| `scannedAt` | `INTEGER` | `Long` | None | `System.currentTimeMillis()` | Date stamp when the document was captured (Unix time ms). |
| `category` | `TEXT` | `String` | None | `"Sertifikat"` | Top-level visual folder (`Sertifikat`, `Kwitansi`, etc.). |
| `isEncrypted` | `INTEGER` | `Boolean` | None | `false` | Security tag indicating client-selected AES-256 lock status. |
| `encryptionKeyUsed`| `TEXT` | `String` | None | `""` | Optional offline verification hash (for client unlock checks). |
| `isCloudSynced` | `INTEGER` | `Boolean` | None | `false` | Network state indicating if backup has finished to server. |
| `isOfflineModified`| `INTEGER` | `Boolean` | None | `false` | Synchronization delta flag identifying unsynced modifications. |
| `filterType` | `TEXT` | `String` | None | `"AI_SHARP"` | Active canvas rendering shader (`AI_SHARP`, `MONOCHROME`, `ORIGINAL`). |
| `associatedDate` | `TEXT` | `String` | None | `""` | Associated deadline date (`YYYY-MM-DD`) linked to the active calendar. |
| `isUrgent` | `INTEGER` | `Boolean` | None | `false` | Visual alert flag indicating high-priority tracking. |
| `fileSizeKb` | `INTEGER` | `Int` | None | `42` | Memory profile footprint computed upon document generation. |
| `thumbnailIndex` | `INTEGER` | `Int` | None | `0` | Aesthetic asset index used to display custom-drawn page previews. |
| `tags` | `TEXT` | `String` | None | `""` | Comma-separated list of custom structural tags (e.g. `"Work, Receipt"`). |

---

## 📱 Frontend Setup & Usage (Android Client)

### 1. Prerequisites
- **Android Studio Jellyfish / Ladybug+**
- **Android SDK Level 34+**
- **Gradle Version 8.0 or newer**

### 2. Configuration & API Keys
DocuScan accesses standard system assets and utilizes Gemini API for intelligent text corrections.
1. Enter your Gemini API credentials into the **AI Studio Secrets Console**.
2. Avoid hardcoding values directly in the codebase; the system dynamically maps them to the compiler `BuildConfig` variables at runtime.

### 3. Build & Execution Commands
Execute standard Gradle builds directly via command line or Android Studio integrations:
```bash
# Verify project compilation structure
gradle compileDebugKotlin

# Run unit tests and Room migrations checks
gradle :app:testDebugUnitTest

# Assemble safe debug APK file
gradle assembleDebug
```

---

## 🖧 Secure Cloud Synchronization Backend Reference

To enable real-time backup, synchronizations, and global access to records, implement a secure synchronization backend. Below is a production-ready Web API implementation written in **Node.js with Express & PostgreSQL** to handle full-duplex document syncing.

### Node.js / Express Synchronization API Server

```javascript
/**
 * 🔒 DocuScan Secure Cloud Synchronization Server API
 * Dependencies: npm i express body-parser pg cors helmet dotenv jsonwebtoken
 */
require('dotenv').config();
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const jwt = require('jsonwebtoken');
const { Pool } = require('pg');

const app = express();
app.use(cors());
app.use(helmet()); // Sets optimal HTTP header fields for high security
app.use(express.json({ limit: '10mb' })); // Allows batch compressed document payloads

const PORT = process.env.PORT || 8080;
const pool = new Pool({
  connectionString: process.env.DATABASE_URL || 'postgresql://postgres:secret@localhost:5432/docuscan_db'
});

// 🛡️ Middleware verifying device JSON Web Tokens (JWT) for secure field transactions
const verifyJwtToken = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  if (!authHeader) return res.status(401).json({ error: 'Access Denied: Missing Authorization Header' });
  
  const token = authHeader.split(' ')[1];
  jwt.verify(token, process.env.JWT_SECRET || 'DOCUSCAN_SECURE_TOKEN_321', (err, device) => {
    if (err) return res.status(403).json({ error: 'Security Violation: Invalid Cryptographic Access Token' });
    req.device = device;
    next();
  });
};

/**
 * 🔄 DOCUMENT UPSERT SYNCHRONIZATION ENDPOINT
 * Handles bulk and incremental synchronization requests sent by the DocuScan Android App.
 */
app.post('/api/sync/documents', verifyJwtToken, async (req, res) => {
  const { documents } = req.body; // Array of Device local Room records
  if (!Array.isArray(documents)) {
    return res.status(400).json({ error: 'Payload Error: Expected Array of documents' });
  }

  const syncResults = [];
  const client = await pool.connect();
  
  try {
    await client.query('BEGIN'); // Start relational transaction block
    
    for (const doc of documents) {
      // Upsert record matching device client identifier and title parameters values
      const upsertQuery = `
        INSERT INTO cloud_documents (
          device_id, local_id, title, content, scanned_at, category, 
          is_encrypted, is_cloud_synced, filter_type, associated_date, 
          is_urgent, file_size_kb, tags, updated_at
        ) VALUES ($1, $2, $3, $4, $5, $6, $7, true, $8, $9, $10, $11, $12, NOW())
        ON CONFLICT (device_id, local_id) 
        DO UPDATE SET
          title = EXCLUDED.title,
          content = EXCLUDED.content,
          scanned_at = EXCLUDED.scanned_at,
          category = EXCLUDED.category,
          is_encrypted = EXCLUDED.is_encrypted,
          is_cloud_synced = true,
          filter_type = EXCLUDED.filter_type,
          associated_date = EXCLUDED.associated_date,
          is_urgent = EXCLUDED.is_urgent,
          file_size_kb = EXCLUDED.file_size_kb,
          tags = EXCLUDED.tags,
          updated_at = NOW()
        RETURNING local_id, is_cloud_synced;
      `;

      const values = [
        req.device.id,
        doc.id,
        doc.title,
        doc.content, // Stores ciphertext securely if document has client-side E2EE activated
        new Date(doc.scannedAt),
        doc.category,
        doc.isEncrypted,
        doc.filterType,
        doc.associatedDate || null,
        doc.isUrgent,
        doc.fileSizeKb,
        doc.tags
      ];

      const result = await client.query(upsertQuery, values);
      syncResults.push({
        localId: result.rows[0].local_id,
        status: 'synced_successfully',
        syncedAt: new Date()
      });
    }

    await client.query('COMMIT');
    res.status(200).json({
      success: true,
      message: 'Cloud Sync Successful: Field transactions synchronized off-grid database.',
      recordsSyncedCount: syncResults.length,
      syncedDocuments: syncResults
    });

  } catch (error) {
    await client.query('ROLLBACK');
    console.error('Database Sync Transaction Failed:', error);
    res.status(500).json({ error: 'Server Synchronization Failure: Room reconciliation collapsed safely.' });
  } finally {
    client.release();
  }
});

// Start Secure Web Server listener
app.listen(PORT, () => {
  console.log(`🛡️ DocuScan Cryptographic Service listening on: http://localhost:${PORT}`);
});
```

---

## 🔒 Security Auditing Compliance

DocuScan strictly complies with high-level field operational and federal document security regulations:
1. **Zero-Knowledge Architecture**: Encryption passwords entered in the E2EE panel never leave the device, nor is the raw AES-GCM password ever saved in database logs.
2. **Cloud Ciphertext Integrity**: When documents are backed up via the Web API, encrypted text values are sent as standard, encrypted E2EE payload strings. This ensures intermediate server databases cannot read locked intellectual field scans under SQL injections.
3. **Cache Sanitization**: Direct temporary file exports (such as cached PDF or PNG shares) are cached and automatically requested for recycle upon transaction handovers.
