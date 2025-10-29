package com.nearnet.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import com.nearnet.R
import com.nearnet.ui.model.FileMetadata
import com.nearnet.ui.model.IMAGE_ATTACHMENT_MAX_SIZE
import com.nearnet.ui.model.MINIATURE_MAX_SIZE
import com.nearnet.ui.model.decodeBase64Bitmap

fun transformToMiniature(bitmap: Bitmap, ): Bitmap {
    val miniatureSize = IMAGE_ATTACHMENT_MAX_SIZE
    val maxSize = maxOf(bitmap.width, bitmap.height)
    if (maxSize > miniatureSize) {
        val scale = miniatureSize.toFloat() / maxSize
        return bitmap.scale(
            (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
    }
    return bitmap
}

@Composable
fun AttachmentImageMiniature(base64: String, onClick: (() -> Unit)? = null) {
    val bitmap = if (base64.isNotEmpty()) decodeBase64Bitmap(base64) else null
    if (bitmap != null) {
        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        Image(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = "Miniature",
            modifier = Modifier
                .width(if (aspect < 1) MINIATURE_MAX_SIZE * aspect else MINIATURE_MAX_SIZE)
                .height(if (aspect > 1) MINIATURE_MAX_SIZE / aspect else MINIATURE_MAX_SIZE)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp)
                )
                .clip(RoundedCornerShape(6.dp))
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        )
    }
}

@Composable
fun AttachmentFileMiniature(metadata: FileMetadata? = null, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Icon(
            painter = painterResource(R.drawable.file),
            contentDescription = "Miniature",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(MINIATURE_MAX_SIZE / 2)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp)
                )
                .clip(RoundedCornerShape(6.dp))
        )
        if (metadata != null) {
            Text(
                text = metadata.filename,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
