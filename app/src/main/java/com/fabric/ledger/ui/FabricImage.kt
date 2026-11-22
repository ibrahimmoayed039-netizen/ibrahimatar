package com.fabric.ledger.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val MAX_SIDE = 800

/** ذاكرة مؤقتة للصور المصغّرة (≈16 ميغابايت) كي لا تُفك القائمة كل مرة */
private val imageCache = object : LruCache<String, Bitmap>(16 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
}

/** يقرأ الصورة (من uri أو bitmap جاهز)، يصحح الدوران، يصغّرها لأقصى 800 بكسل، ويحفظها JPEG داخل مجلد التطبيق. يرجع المسار أو null */
fun saveFabricImage(context: Context, uri: Uri? = null, bitmap: Bitmap? = null): String? {
    return try {
        var bmp: Bitmap = bitmap ?: run {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri!!)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val deg = resolver.openInputStream(uri)?.use { s ->
                when (android.media.ExifInterface(s).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
                }
            } ?: 0f
            if (deg != 0f) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(deg) }, true) else decoded
        }
        val longest = maxOf(bmp.width, bmp.height)
        if (longest > MAX_SIDE) {
            val k = MAX_SIDE.toFloat() / longest
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
        }
        val dir = File(context.filesDir, "fabric_images").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

fun deleteFabricImage(path: String) {
    if (path.isNotBlank()) runCatching { File(path).delete() }
}

private fun decodeSampled(path: String, reqPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= reqPx) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** صورة الصنف: مربع مدوّر؛ إن لم توجد صورة يظهر أول حرف من الاسم */
@Composable
fun FabricThumb(path: String, name: String, size: Dp, modifier: Modifier = Modifier, decodePx: Int = 256, fit: Boolean = false) {
    val key = "$path@$decodePx"
    val bmp by produceState<Bitmap?>(if (path.isBlank()) null else imageCache.get(key), key) {
        value = if (path.isBlank()) null else imageCache.get(key) ?: withContext(Dispatchers.IO) {
            runCatching { decodeSampled(path, decodePx) }.getOrNull()?.also { imageCache.put(key, it) }
        }
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) Image(
            b.asImageBitmap(), name, Modifier.fillMaxSize(),
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop
        ) else Text(name.trim().take(1), style = MaterialTheme.typography.titleLarge)
    }
}

/** عرض الصورة بحجم كبير */
@Composable
fun FabricImageDialog(path: String, name: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = { FabricThumb(path, name, 320.dp, Modifier.fillMaxWidth(), decodePx = MAX_SIDE, fit = true) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

/** اختيار صورة الصنف: من المعرض أو بالكاميرا، مع إمكانية الحذف. onChange يستلم المسار الجديد ("" = بدون صورة) */
@Composable
fun FabricImagePicker(path: String, name: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) saveFabricImage(context, uri = uri)?.let(onChange)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) saveFabricImage(context, bitmap = bmp)?.let(onChange)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FabricThumb(path, name, 88.dp, decodePx = 400)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("من المعرض") }
            OutlinedButton(onClick = { camera.launch(null) }) { Text("التقاط صورة") }
            if (path.isNotBlank()) TextButton(onClick = { onChange("") }) { Text("حذف الصورة", color = MaterialTheme.colorScheme.error) }
        }
    }
}
