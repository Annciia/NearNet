package com.nearnet.ui.component

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.core.graphics.scale
import com.nearnet.ui.model.LocalViewModel
import com.nearnet.ui.model.PopupType
import com.nearnet.ui.model.decodeBase64Bitmap
import com.nearnet.ui.model.encodeBase64Bitmap
import com.nearnet.ui.model.getBitmapFromUri

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

@Composable
fun AvatarPicker(avatarDefault: Int, avatarBase64: String, onAvatarChange: (String) -> Unit) {
    val context = LocalContext.current
    val vm = LocalViewModel.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = transformToAvatar(getBitmapFromUri(context, uri))
            val bitmapBase64 = encodeBase64Bitmap(bitmap)
            onAvatarChange(bitmapBase64)
        }
    }
    val avatarBitmap = if (avatarBase64.isNotEmpty()) decodeBase64Bitmap(avatarBase64) else null
    if (avatarBitmap == null) {
        Icon(
            painter = painterResource(avatarDefault),
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
                .clickable {
                    vm.selectPopup(PopupType.EDIT_AVATAR, {imageAction : String ->
                        if (imageAction =="changeImage") {launcher.launch("image/*")}
                        if (imageAction =="removeImage") {onAvatarChange("")}
                    })
                }
        )
    }
}

@Composable
fun AvatarCircle(avatarBase64: String, defaultAvatar: Int) {
    val avatarBitmap = if (avatarBase64.isNotEmpty()) decodeBase64Bitmap(avatarBase64) else null
    if (avatarBitmap != null) {
        Image(
            painter = BitmapPainter(avatarBitmap.asImageBitmap()),
            contentDescription = "Avatar",
            modifier = Modifier.size(80.dp).clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
        )
    } else {
        Icon(
            painter = painterResource(defaultAvatar),
            tint = MaterialTheme.colorScheme.onPrimary,
            contentDescription = "Avatar",
            modifier = Modifier.size(80.dp).clip(CircleShape)
            .padding(2.dp)
        )
    }
}

@Composable
fun AvatarSquare(avatarBase64: String, defaultAvatar: Int) {
    val avatarBitmap = if (avatarBase64.isNotEmpty()) decodeBase64Bitmap(avatarBase64) else null
    if (avatarBitmap != null) {
        Image(
            painter = BitmapPainter(avatarBitmap.asImageBitmap()),
            contentDescription = "Avatar",
            modifier = Modifier
                .size(50.dp)
                .background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(6.dp)),
        )
    } else {
        Icon(
            painter = painterResource(defaultAvatar),
            tint = MaterialTheme.colorScheme.onPrimary,
            contentDescription = "Avatar",
            modifier = Modifier
                .size(50.dp)
                .background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(6.dp)),
        )
    }
}
