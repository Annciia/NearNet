package com.nearnet.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearnet.R
import com.nearnet.sessionlayer.data.model.RoomData
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.nearnet.sessionlayer.data.model.Message
import com.nearnet.sessionlayer.data.model.UserData
import com.nearnet.ui.model.FileMetadata
import com.nearnet.ui.model.MESSAGE_MAX_LENGTH
import com.nearnet.ui.model.MessageType
import com.nearnet.ui.model.decodeBase64ByteArray
import com.nearnet.ui.model.decodeMessageFileMetadata
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageItem(message: Message, user: UserData? = null, room: RoomData? = null, ellipse: Boolean = false, attachmentClickable : Boolean = true, onClick: ((message: Message, room: RoomData?)->Unit)? = null) {
//    var date = ""
//    if (message.timestamp.isNotEmpty()) {
//        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
//        date = LocalDateTime.parse(message.timestamp, formatter).format(DateTimeFormatter.ofPattern("yyyy-MM-dd • HH:mm"))
//    }
    val context = LocalContext.current

    // Date conversion
    val date = try {
        val tsMillis = message.timestamp.toLong() // zamień String na Long
        val localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(tsMillis), ZoneId.systemDefault())
        localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd • HH:mm"))
    } catch (e: Exception) {
        "" // jeśli coś pójdzie nie tak
    }

    // File message handling
    val fileMetadata = remember { mutableStateOf<FileMetadata?>(null) }
    val fileBase64 = remember { mutableStateOf<String?>(null) }
    if (message.messageType == MessageType.FILE.name) {
        val (metadata, base64) = decodeMessageFileMetadata(message.message)
        fileMetadata.value = metadata
        fileBase64.value = base64
    }
    if (message.messageType == MessageType.IMAGE.name) {
        fileMetadata.value = FileMetadata(filename = "image.png", mime = "image/png")
        fileBase64.value = message.message
    }
    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(fileMetadata.value?.mime ?: "*/*")
    ) {
        uri ->
            if (uri != null) {
                val bytes = decodeBase64ByteArray(fileBase64.value ?: "")
                if (bytes != null) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(bytes)
                    }
                }
            }
    }

    // UI
    Row(
        modifier = Modifier.then(
            if (onClick != null) {
                Modifier.clickable { onClick(message, room) }
            } else {
                Modifier
            }).padding(vertical = 10.dp),
    ) {
        if (room != null) {
            AvatarSquare(room.avatar, R.drawable.image)
        } else {
            AvatarSquare(user?.avatar ?: "", R.drawable.spacecat)
        }
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(5.dp)
                .fillMaxWidth()
        ){
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f)
                ) {
                    if (room != null) {
                        Text(
                            text = room.name + "\u00A0•\u00A0",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text(
                        text = user?.name ?: "Unknown",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp
                    ),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            if (message.messageType == MessageType.TEXT.name || message.messageType == "text") {
                if (message.message.length > MESSAGE_MAX_LENGTH) return
                Text(
                    text = message.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = if (!ellipse) Int.MAX_VALUE else 1,
                    overflow = if (!ellipse) TextOverflow.Clip else TextOverflow.Ellipsis,
                )
            } else if (message.messageType == MessageType.IMAGE.name) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AttachmentImageMiniature(
                        base64 = message.message,
                        onClick = if (attachmentClickable==true) {
                            { saveFileLauncher.launch(fileMetadata.value?.filename ?: "image.png") }
                        } else null
                    )
                }
            } else if (message.messageType == MessageType.FILE.name) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AttachmentFileMiniature(
                        metadata = fileMetadata.value,
                        onClick = if (attachmentClickable==true) {
                            { saveFileLauncher.launch(fileMetadata.value?.filename ?: "file") }
                        } else null
                    )
                }
            }
        }
    }
}
