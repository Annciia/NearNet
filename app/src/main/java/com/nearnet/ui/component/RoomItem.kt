package com.nearnet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nearnet.R
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.ui.theme.standardIconStyleTransparent

@Composable
fun RoomItem(room : RoomData, onClick : (RoomData) -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 10.dp).clickable { onClick(room) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarSquare(room.avatar, R.drawable.image)
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(5.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                if (room.description != null) {
                    Text(
                        text = room.description!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(5.dp))
            Column(modifier = Modifier.padding(start = 7.dp, end = 7.dp)) {
                if (room.isPrivate == true) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Private room",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .standardIconStyleTransparent()
                    )
                } else {
                    Spacer(modifier = Modifier.width(22.dp))
                }
            }
        }
    }
}
