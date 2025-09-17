package com.nearnet.ui.model

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearnet.Message
import com.nearnet.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

//zawiera zmienne przechowujące stan aplikacji
class NearNetViewModel: ViewModel() {

    //Rooms
    private val roomsMutable = MutableStateFlow(listOf<Room>())
    val rooms = roomsMutable.asStateFlow()

    //Selected room
    private val selectedRoomMutable = MutableStateFlow<Room?>(null)
    val selectedRoom = selectedRoomMutable.asStateFlow()

    //Messages
    private val messagesMutable = MutableStateFlow(listOf<Message>())
    val messages = messagesMutable.asStateFlow()


    //constructor to VievModel
    init {

    }

    fun loadRooms() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch rooms here.
            //roomsMutable.value = getUserRoomList(idUser)
            roomsMutable.value = listOf(
                Room(0, "Stormvik games", "Witaj! Jestem wikingiem.", true),
                Room(1, "You cat", "Meeeeeeeeeeeeeeeow!", false),
                Room(2, "虫籠のカガステル", "No comment needed. Just join!", false),
                Room(3, "Biohazard", "Be careful.", false),
                Room(4, "Mibik game server", "Mi mi mi! It's me.", false),
                Room(5, "Fallout", null, true),
                Room(7, "My new world", "Don't join. It's private", true),
                Room(8, "The Lord of the Rings: The Battle for the Middle Earth", "Elen", false),
            )
        }
    }
    fun selectRoom(room : Room) {
        loadMessages(room)
        selectedRoomMutable.value = room
    }
    fun loadMessages(room: Room){
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch messages here.
            //messagesMutable.value = getMessageHistory(idRoom = room.id, offset = 0, numberOfMessages = -1)
            messagesMutable.value = listOf(
                Message(0, "Orci Kätter", "Lorem ipsum dolor sit amet, consectetur adipiscing elit."),
                Message(1, "Mauris ", "Proin a eros quam. Ut sit amet ultrices nisi. Pellentesque ac tristique nisl, id imperdiet est. Integer scelerisque leo at blandit blandit."),
                Message(2, "Orci Kätter", "Fusce sed ligula turpis. Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Curabitur ac consequat nisi. Phasellus libero nibh, finibus non egestas in, egestas in lorem. Pellentesque nec facilisis erat, in pulvinar ipsum. Morbi congue viverra lectus quis fermentum. Duis sagittis est dapibus venenatis vestibulum.")
            )
        }
    }
    fun sendMessage(messageText : String, room : Room){
        viewModelScope.launch{
            //sendMessage(idRoom, Message)
            val message = Message (id = -1, userNameSender = "Orci Kätter", content = messageText)
            // TODO Call asynchronous function to send messages
            //sendMessage(room.id, message)
            messagesMutable.value += message
        }
    }

}

val LocalViewModel = staticCompositionLocalOf<NearNetViewModel> {
    error("No NearNetViewModel provided")
}
