package com.nearnet.ui.component

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nearnet.R
import com.nearnet.ui.model.FILE_ATTACHMENT_MAX_SIZE
import com.nearnet.ui.model.LocalViewModel
import com.nearnet.ui.model.MessageType
import com.nearnet.ui.model.decodeMessageFileMetadata
import com.nearnet.ui.model.encodeBase64Bitmap
import com.nearnet.ui.model.encodeBase64ByteArray
import com.nearnet.ui.model.encodeMessageFileMetadata
import com.nearnet.ui.model.getBitmapFromUri
import com.nearnet.ui.model.getBytesFromUri
import com.nearnet.ui.model.getFileMetadataFromUri
import com.nearnet.ui.theme.standardIconStyle

@Composable
fun ConversationPanel() {
    val context = LocalContext.current
    val vm = LocalViewModel.current
    var messageText by rememberSaveable { mutableStateOf("") }
    var hideAttachmentPanel by rememberSaveable { mutableStateOf(false) }
    val imageAttachment = rememberSaveable { mutableStateOf<String?>(null) }
    val fileAttachment = rememberSaveable { mutableStateOf<String?>(null) }
    val loadImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = transformToMiniature(getBitmapFromUri(context, uri))
            val base64 = encodeBase64Bitmap(bitmap)
            imageAttachment.value = base64
        }
    }
    val loadFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bytes = getBytesFromUri(context, uri)
            if (bytes.size >= FILE_ATTACHMENT_MAX_SIZE) {
                val size = FILE_ATTACHMENT_MAX_SIZE / 1024
                Toast.makeText(context, "File is too large (max $size KiB).", Toast.LENGTH_SHORT).show()
                return@let
            }
            val base64 = encodeBase64ByteArray(bytes)
            val metadata = getFileMetadataFromUri(context, uri)
            fileAttachment.value = encodeMessageFileMetadata(metadata, base64)
        }
    }
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (hideAttachmentPanel == false) {
            ConversationPanelButton(
                res = R.drawable.file,
                conversationButtonDescription ="Attach a file",
                onClick = {
                    loadFileLauncher.launch("*/*")
                }
            )
            Spacer(Modifier.width(5.dp))
            ConversationPanelButton(
                res = R.drawable.image,
                conversationButtonDescription="Attach an image",
                onClick = {
                    loadImageLauncher.launch("image/*")
                }
            )
        } else {
            ConversationPanelButton(
                imageVector = Icons.Default.KeyboardArrowRight,
                conversationButtonDescription ="Show attachment buttons",
                onClick = { hideAttachmentPanel = false })
        }
        Spacer(Modifier.width(5.dp))
        val image = imageAttachment.value
        val file = fileAttachment.value
        if (image != null || file != null) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.TopEnd
                ) {
                    if (image != null) {
                        AttachmentImageMiniature(base64 = image)
                    } else if(file != null) {
                        val (metadata) = decodeMessageFileMetadata(file)
                        AttachmentFileMiniature(metadata = metadata)
                    }
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .padding(end = 2.dp, top = 2.dp)
                            .standardIconStyle()
                            .clickable {
                                imageAttachment.value = null
                                fileAttachment.value = null
                            }
                    )
                }
            }
        } else {
            MessageEditorField(
                modifier = Modifier.weight(1f),
                placeholderText = "Type message...",
                value = messageText,
                onValueChange = { text ->
                    messageText = text
                    hideAttachmentPanel = text.isNotEmpty()
                }
            )
        }
        Spacer(Modifier.width(5.dp))
        ConversationPanelButton(
            res = R.drawable.send,
            onClick = {
                val selectedRoom = vm.selectedRoom.value
                if (selectedRoom != null) {
                    val image = imageAttachment.value
                    val file = fileAttachment.value
                    if (image != null) {
                        vm.sendMessage(image, selectedRoom, MessageType.IMAGE)
                    } else if (file != null) {
                        vm.sendMessage(file, selectedRoom, MessageType.FILE)
                    } else if (messageText.isNotBlank()) {
                        vm.sendMessage(messageText, selectedRoom, MessageType.TEXT)
                    }
                }
                imageAttachment.value = null
                fileAttachment.value = null
                messageText = ""
            }
        )

    }
}
@Composable
fun ConversationPanelButton(modifier: Modifier = Modifier, res: Int? = null, imageVector: ImageVector? = null, conversationButtonDescription: String="", onClick: (() -> Unit) = {}) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.size(36.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        if (res != null) {
            Icon(
                painter = painterResource(res),
                contentDescription = conversationButtonDescription,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = modifier.fillMaxSize(0.7f),
            )
        } else if (imageVector != null){
            Icon(
                imageVector = imageVector,
                contentDescription = conversationButtonDescription,
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
