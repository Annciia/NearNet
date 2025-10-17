package com.nearnet.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import java.io.IOException
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.nearnet.R
import java.io.ByteArrayOutputStream

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
    throw IOException("Unable to decode")
}

fun transformToAvatar(bitmap: Bitmap): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val cropSize = minOf(width, height)
    val cropX = (width - cropSize) / 2
    val cropY = (height - cropSize) / 2
    return Bitmap
        .createBitmap(bitmap, cropX, cropY, cropSize, cropSize)
        .scale(256,256, true)
}

fun encodeBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG,80, outputStream)
    val bytes = outputStream.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

fun decodeBase64(base64: String): Bitmap? {
    val bytes = Base64.decode(base64, Base64.NO_WRAP)
    return if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
}

@Composable
fun AvatarPicker(avatarBase64: String, onAvatarChange: (String) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = transformToAvatar(getBitmapFromUri(context, uri))
            val bitmapBase64 = encodeBase64(bitmap)
            onAvatarChange(bitmapBase64)
        }
    }
    val avatarBitmap = if (avatarBase64.isNotEmpty()) decodeBase64(avatarBase64) else null
    if (avatarBitmap == null) {
        Icon(
            painter = painterResource(R.drawable.image),
            contentDescription = "Avatar",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp)
                )
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(6.dp))
                .clickable { launcher.launch("image/*") }

                //.background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp))
                //.clip(RoundedCornerShape(6.dp))
                //.border(2.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(6.dp)),
        )
    } else {
        Image(
            painter = BitmapPainter(avatarBitmap.asImageBitmap()),
            contentDescription = "Avatar",
            modifier = Modifier
                .size(100.dp)
                .clip(shape = RoundedCornerShape(6.dp))
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp)
                )
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(6.dp))
                .clickable { launcher.launch("image/*") }
        )
    }
}

@Composable
fun Avatar(avatarBase64: String) {
    val avatarBitmap = if (avatarBase64.isNotEmpty()) decodeBase64(avatarBase64) else null
    if (avatarBitmap != null) {
        Image(
            painter = BitmapPainter(avatarBitmap.asImageBitmap()),
            contentDescription = "avatar"
        )
    }
}
