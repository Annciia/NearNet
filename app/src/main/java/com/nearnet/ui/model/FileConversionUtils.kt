package com.nearnet.ui.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException


class FileMetadata(
    val mime: String,
    val filename: String
)

fun getBitmapFromUri(context: Context, uri: Uri): Bitmap {
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        val bytes = inputStream.readBytes()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val exif = ExifInterface(bytes.inputStream())
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    throw IOException("Unable to load or decode")
}

fun getBytesFromUri(context: Context, uri: Uri): ByteArray {
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        return inputStream.readBytes()
    }
    throw IOException("Unable to load")
}

fun getMIMEFromUri(context: Context, uri: Uri): String? {
    return context.contentResolver.getType(uri)
}

fun getFilenameFromUri(context: Context, uri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && nameIndex >= 0) {
            name = it.getString(nameIndex)
        }
    }
    return name
}

fun getFileMetadataFromUri(context: Context, uri: Uri): FileMetadata {
    return FileMetadata(
        mime = getMIMEFromUri(context, uri) ?: "",
        filename = getFilenameFromUri(context, uri) ?: ""
    )
}

fun encodeBase64Bitmap(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, IMAGE_COMPRESSION_QUALITY, outputStream)
    val bytes = outputStream.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

fun decodeBase64Bitmap(base64: String): Bitmap? {
    if (base64.isEmpty()) return null
    val bytes = Base64.decode(base64, Base64.NO_WRAP)
    return if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
}

fun encodeBase64ByteArray(bytes: ByteArray): String {
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

fun decodeBase64ByteArray(base64: String): ByteArray? {
    if (base64.isEmpty()) return null
    return Base64.decode(base64, Base64.NO_WRAP)
}

fun encodeMessageFileMetadata(metadata: FileMetadata, base64: String): String {
    return metadata.mime + "\n" + metadata.filename + "\n" + base64;
}

fun decodeMessageFileMetadata(message: String): Pair<FileMetadata, String> {
    val split = message.split("\n", limit = 3)
    val metadata = FileMetadata(
        mime = split.getOrNull(0) ?: "",
        filename = split.getOrNull(1) ?: ""
    )
    return Pair(metadata, split.getOrNull(2) ?: "")
}
