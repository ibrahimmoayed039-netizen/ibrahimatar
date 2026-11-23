package com.fabric.ledger.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** حالة إعداد النسخ التلقائي: mode = "folder" (حفظ صامت في مجلد) أو "whatsapp" (تذكير بإرسالها) */
data class AutoBackupState(val enabled: Boolean, val mode: String, val tree: String, val last: Long)

object AutoBackup {
    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private const val KEEP = 4
    private const val PREFIX = "fabric-ledger-auto-"

    private fun stamp(pattern: String) = SimpleDateFormat(pattern, Locale.US).format(Date())

    /** اسم المجلد المختار للعرض */
    fun folderName(context: Context, tree: Uri): String = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "المجلد المختار"

    /** يحفظ نسخة جديدة داخل المجلد ويُبقي آخر 4 نسخ تلقائية فقط */
    fun saveToFolder(context: Context, tree: Uri, data: BackupData): Boolean {
        val r = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeDocId)
        val name = PREFIX + stamp("yyyyMMdd-HHmm") + ".zip"
        val doc = DocumentsContract.createDocument(r, parent, "application/zip", name) ?: return false
        r.openOutputStream(doc, "wt")!!.use { BackupFiles.write(it, data) }
        runCatching { prune(context, tree, treeDocId) }
        return true
    }

    private fun prune(context: Context, tree: Uri, treeDocId: String) {
        val r = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocId)
        val rows = mutableListOf<Pair<String, String>>()
        r.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(1) ?: ""
                if (n.startsWith(PREFIX)) rows.add(c.getString(0) to n)
            }
        }
        rows.sortedByDescending { it.second }.drop(KEEP).forEach {
            runCatching { DocumentsContract.deleteDocument(r, DocumentsContract.buildDocumentUriUsingTree(tree, it.first)) }
        }
    }

    /** ملف مؤقت للمشاركة عبر واتساب */
    fun writeCacheFile(context: Context, data: BackupData): File {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "fabric-ledger-backup-" + stamp("yyyyMMdd") + ".zip")
        f.outputStream().use { BackupFiles.write(it, data) }
        return f
    }
}
