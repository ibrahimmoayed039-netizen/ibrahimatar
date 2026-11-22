package com.fabric.ledger.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * النسخة الاحتياطية = ملف zip فيه data.json وصور الأصناف داخل images/.
 * يقبل الاسترجاع أيضاً النسخ القديمة بصيغة JSON فقط.
 */
object BackupFiles {
    private const val DATA = "data.json"
    private const val IMAGES = "images/"

    fun write(out: OutputStream, data: BackupData) {
        ZipOutputStream(out).use { zip ->
            // في الملف نحفظ اسم الصورة فقط (المسار الكامل يختلف من هاتف لآخر)
            val portable = data.copy(fabrics = data.fabrics.map { f ->
                f.copy(imagePath = if (f.imagePath.isBlank()) "" else File(f.imagePath).name)
            })
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(Backup.toJson(portable).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val written = mutableSetOf<String>()
            data.fabrics.forEach { f ->
                val file = File(f.imagePath)
                if (f.imagePath.isNotBlank() && file.isFile && written.add(file.name)) {
                    zip.putNextEntry(ZipEntry(IMAGES + file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    fun read(context: Context, input: InputStream): BackupData {
        val bytes = input.readBytes()
        val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) {
            // نسخة JSON قديمة: نُبقي مسار الصورة فقط إن كان الملف موجوداً على هذا الجهاز
            val d = Backup.fromJson(bytes.toString(Charsets.UTF_8))
            return d.copy(fabrics = d.fabrics.map { f ->
                if (f.imagePath.isNotBlank() && !File(f.imagePath).isFile) f.copy(imagePath = "") else f
            })
        }
        var json: String? = null
        val images = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    if (e.name == DATA) json = z.readBytes().toString(Charsets.UTF_8)
                    else if (e.name.startsWith(IMAGES)) images[File(e.name).name] = z.readBytes() // الاسم فقط: لا مسارات خارجية
                }
                e = z.nextEntry
            }
        }
        val data = Backup.fromJson(json ?: throw IllegalArgumentException("ملف غير صالح"))
        val dir = File(context.filesDir, "fabric_images").apply { mkdirs() }
        return data.copy(fabrics = data.fabrics.map { f ->
            if (f.imagePath.isBlank()) f
            else {
                val name = File(f.imagePath).name
                val b = images[name]
                if (b == null) f.copy(imagePath = "")
                else { val target = File(dir, name); target.writeBytes(b); f.copy(imagePath = target.absolutePath) }
            }
        })
    }

    /** بعد الاسترجاع: حذف صور مجلد التطبيق التي لم يعد يشير إليها أي صنف */
    fun cleanupImages(context: Context, used: List<String>) {
        val keep = used.filter { it.isNotBlank() }.map { File(it).absolutePath }.toSet()
        File(context.filesDir, "fabric_images").listFiles()?.forEach { if (it.absolutePath !in keep) it.delete() }
    }
}
