package com.nearnet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nearnet.R
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.sessionlayer.data.model.UserData
import com.nearnet.ui.model.LocalViewModel
import com.nearnet.ui.theme.standardIconStyle

@Composable
fun UserItem(user : UserData, room : RoomData, isKickEnabled: Boolean = false) {
    val vm = LocalViewModel.current
    Row(
        modifier = Modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarSquare(user.avatar, R.drawable.spacecat)
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp)
                )
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Column(modifier = Modifier.padding(start = 7.dp, end = 7.dp)) {
                if (user.id != room.idAdmin && isKickEnabled) {
                    Icon(
                        painter = painterResource(R.drawable.leave_room),
                        contentDescription = "Kick user",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .standardIconStyle()
                            .clickable{
                                vm.removeUserFromRoom(user, room)
                            }
                    )
                } else {
                    Spacer(modifier = Modifier.width(22.dp))
                }
            }
        }
    }
}
