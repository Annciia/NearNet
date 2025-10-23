package com.nearnet.ui.component

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearnet.R
import com.nearnet.sessionlayer.data.model.RoomData
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.nearnet.sessionlayer.data.model.Message
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageItem(message: com.nearnet.sessionlayer.data.model.Message, room: RoomData? = null, ellipse: Boolean = false, onClick: ((message:  com.nearnet.sessionlayer.data.model.Message, room: RoomData?)->Unit)? = null) {
//    var date = ""
//    if (message.timestamp.isNotEmpty()) {
//        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
//        date = LocalDateTime.parse(message.timestamp, formatter).format(DateTimeFormatter.ofPattern("yyyy-MM-dd • HH:mm"))
//    }
    val date = try {
        val tsMillis = message.timestamp.toLong() // zamień String na Long
        val localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(tsMillis), ZoneId.systemDefault())
        localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd • HH:mm"))
    } catch (e: Exception) {
        "" // jeśli coś pójdzie nie tak
    }
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
            //AvatarSquare(message.userAvatar, R.drawable.spacecat)
            AvatarSquare("", R.drawable.spacecat)
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
                        text = message.userId,
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
            Text(
                text = message.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = if (!ellipse) Int.MAX_VALUE else 1,
                overflow = if (!ellipse) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
    }
}
