package com.nearnet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nearnet.R
import com.nearnet.Room
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.ui.theme.standardIconStyleTransparent

@Composable
fun RoomItem(room : RoomData, onClick : (RoomData) -> Unit) {
    Row(
        // can do: If onClick == null => RoomItem is not clickable
        modifier = Modifier.padding(vertical = 10.dp).clickable { onClick(room) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .size(50.dp)
                .background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(6.dp)),
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null)
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
