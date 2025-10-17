package com.nearnet.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nearnet.R
import com.nearnet.ui.model.LocalViewModel

@Composable
fun ConversationPanel() {
    val vm = LocalViewModel.current
    var messageText by rememberSaveable { mutableStateOf("") }
    var hideAttachmentPanel by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (hideAttachmentPanel==false) {
            ConversationPanelButton(
                res = R.drawable.file,
                conversationButtonDescription ="Attach a file")
            Spacer(Modifier.width(5.dp))
            ConversationPanelButton(
                res = R.drawable.image,
                conversationButtonDescription="Attach an image")
        } else {
            ConversationPanelButton(
                imageVector = Icons.Default.KeyboardArrowRight,
                conversationButtonDescription ="Show attachment buttons",
                onClick = { hideAttachmentPanel = false })
        }
        Spacer(Modifier.width(5.dp))
        MessageEditorField(
            modifier = Modifier.weight(1f),
            placeholderText = "Type message...",
            value = messageText,
            onValueChange = { text ->
                messageText = text
                hideAttachmentPanel = text.isNotEmpty()
            }
        )
        Spacer(Modifier.width(5.dp))
        ConversationPanelButton(
            res = R.drawable.send,
            onClick = {
                if (messageText.isNotBlank()) {
                    vm.sendMessage(messageText, vm.selectedRoom.value!!)
                    messageText = ""
                }
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
