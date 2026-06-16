package id.scan.docuscan.data

import kotlinx.coroutines.flow.Flow

class DocumentRepository(private val documentDao: DocumentDao) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val totalDocumentsCount: Flow<Int> = documentDao.countDocumentsFlow()

    suspend fun getDocumentById(id: Int): DocumentEntity? {
        return documentDao.getDocumentById(id)
    }

    fun getDocumentsForDate(date: String): Flow<List<DocumentEntity>> {
        return documentDao.getDocumentsForDate(date)
    }

    suspend fun insert(document: DocumentEntity): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun update(document: DocumentEntity) {
        documentDao.updateDocument(document)
    }

    suspend fun delete(document: DocumentEntity) {
        documentDao.deleteDocument(document)
    }

    suspend fun deleteById(id: Int) {
        documentDao.deleteDocumentById(id)
    }

    suspend fun getDocumentsOlderThan(timestamp: Long): List<DocumentEntity> {
        return documentDao.getDocumentsOlderThan(timestamp)
    }

    suspend fun deleteDocumentsOlderThan(timestamp: Long) {
        documentDao.deleteDocumentsOlderThan(timestamp)
    }
}
