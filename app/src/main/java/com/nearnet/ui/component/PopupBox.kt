package com.nearnet.ui.component

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nearnet.sessionlayer.data.model.RoomData
import com.nearnet.ui.model.LocalViewModel
import com.nearnet.ui.model.PopupContext
import com.nearnet.ui.model.PopupContextApprovalData
import com.nearnet.ui.model.PopupType
import com.nearnet.ui.model.ProcessEvent

@Composable
fun PopupBox() {
    val vm = LocalViewModel.current
    val popupContext = vm.selectedPopup.collectAsState().value
    if (popupContext != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Dialog(
                onDismissRequest = { vm.closePopup() },
                properties = DialogProperties(
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .background(Color.White, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(10.dp)
                ) {
                    when (popupContext.type) {
                        PopupType.DELETE_USER_AUTHORIZATION -> DeleteUserAuthorizationPopup()
                        PopupType.LOGOUT_CONFIRMATION -> LogoutConfirmationPopup()
                        PopupType.DELETE_ROOM_CONFIRMATION -> DeleteRoomConfirmationPopup()
                        PopupType.JOIN_ROOM_CONFIRMATION -> JoinRoomConfirmationPopup(popupContext)
                        PopupType.JOIN_ROOM_APPROVAL -> JoinRoomApprovalPopup(popupContext)
                    }
                }
            }
        }
    }
}

@Composable
fun DialogPopup(
    title: String,
    text: String,
    acceptEnabled: Boolean = true,
    onAccept: () -> Unit = {},
    onCancel: () -> Unit = {},
    content: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
        if (content !== null) {
            Spacer(Modifier.height(20.dp))
            content()
        }
        Spacer(Modifier.height(20.dp))
        Row {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onCancel() },
            ) {
                Text("✖\uFE0F Cancel")
            }
            Spacer(Modifier.width(10.dp))
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onAccept() },
                enabled = acceptEnabled
            ) {
                Text("✔\uFE0F Accept")
            }
        }
    }
}

@Composable
fun DeleteUserAuthorizationPopup() {
    val vm = LocalViewModel.current
    val password = remember { mutableStateOf("") }
    DialogPopup(
        title = "Authorization",
        text = "Are you sure you want to delete your account? Your data will be permanently lost. Please enter your password to confirm.",
        acceptEnabled = password.value.isNotBlank(),
        onAccept = {
            vm.closePopup()
            vm.clearQueuedPopups()
            vm.deleteUser(password.value)
        },
        onCancel = {
            vm.closePopup()
        }
    ) {
        PlainTextField(
            value = password.value,
            onValueChange = { text -> password.value = text },
            placeholderText = "password",
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun LogoutConfirmationPopup() {
    val vm = LocalViewModel.current
    DialogPopup(
        title = "Logging out",
        text = "Are you sure you want to log out?",
        onAccept = {
            vm.closePopup()
            vm.clearQueuedPopups()
            vm.logOutUser()
        },
        onCancel = {
            vm.closePopup()
        }
    )
}

@Composable
fun DeleteRoomConfirmationPopup() {
    val vm = LocalViewModel.current
    val selectedRoom = vm.selectedRoom.collectAsState().value
    DialogPopup(
        title = "Deleting the room",
        text = "Are you sure you want to delete this room?",
        onAccept = {
            vm.closePopup()
            if (selectedRoom != null) {
                vm.deleteRoom(selectedRoom)
            }
        },
        onCancel = {
            vm.closePopup()
        }
    )
}

@Composable
fun JoinRoomConfirmationPopup(popupContext: PopupContext) {
    val vm = LocalViewModel.current
    val room = popupContext.data as RoomData
    val password = remember { mutableStateOf("") }
    DialogPopup(
        title = "Join room",
        text = "Are you sure you want to join the room \"" + room.name + "\"?",
        acceptEnabled = password.value.isNotBlank() || !room.isPrivate,
        onAccept = {
            vm.closePopup()
            vm.joinRoom(room, password.value)
        },
        onCancel = {
            vm.closePopup()
        }
    ) {
        if (!room.isPrivate) {
            Text(
                text = "Confirm and join the fun!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Column {
                Text(
                    text = "This room is private! Please enter the password to join",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.height(5.dp))
                PlainTextField(
                    value = password.value,
                    onValueChange = { text -> password.value = text },
                    placeholderText = "password",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "or",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        vm.closePopup()
                        vm.joinRoomRequest(room) //prośba do Admina wysłana
                    }
                ) {
                    Text(text = "Request admin approval")
                }
            }
        }
    }
}

@Composable
fun JoinRoomApprovalPopup(popupContext: PopupContext) {
    val context = LocalContext.current
    val vm = LocalViewModel.current
    val data = popupContext.data as PopupContextApprovalData
    val approveInProgress = remember { mutableStateOf(false) }
    DialogPopup(
        title = "Someone is knocking on your room door",
        text = data.user.name + " would like to join " + data.room.name + ".\nDo you accept?",
        acceptEnabled = !approveInProgress.value,
        onAccept = {
            approveInProgress.value = true
            vm.joinRoomAdminApprove(data.user, data.room, true)
        },
        onCancel = {
            //vm.closePopup()
            approveInProgress.value = true
            vm.joinRoomAdminApprove(data.user, data.room, false)
        }
    )
    LaunchedEffect(Unit) {
        vm.joinRoomAdminApproveEvent.collect{ event ->
            when (event) {
                is ProcessEvent.Success -> {
                    vm.closePopup()
                }
                is ProcessEvent.Error -> {
                    Toast.makeText(context, event.err, Toast.LENGTH_SHORT).show()
                }
            }
            approveInProgress.value = false
        }
    }
}
