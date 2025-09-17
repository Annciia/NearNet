package com.nearnet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nearnet.Message
import com.nearnet.R

@Composable
fun MessageItem(message: Message) {
    Row(
        modifier = Modifier.padding(vertical = 10.dp),
    ) {
        Icon(
            modifier = Modifier
                .size(50.dp)
                .background(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(6.dp)),
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "user avatar")
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
            Text(
                text = message.userNameSender,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = message.content,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
