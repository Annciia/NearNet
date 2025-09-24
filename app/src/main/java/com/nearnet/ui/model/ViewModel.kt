package com.nearnet.ui.model

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearnet.Message
import com.nearnet.Room
import com.nearnet.User
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

var myRoomsList = listOf(
    Room(0, "Stormvik games", "Witaj! Jestem wikingiem.", true),
    Room(1, "You cat", "Meeeeeeeeeeeeeeeow!", false),
    Room(2, "虫籠のカガステル", "No comment needed. Just join!", false),
    Room(3, "Biohazard", "Be careful.", false),
    Room(4, "Mibik game server", "Mi mi mi! It's me.", false),
    Room(5, "Fallout", null, true),
    Room(6, "My new world", "Don't join. It's private", true),
    Room(7, "The Lord of the Rings: The Battle for the Middle Earth", "Elen", false),
)
var discoverRoomsList = listOf(
    Room(0, "Adventure cat games", "Dołącz do kociej przygody.", true),
    Room(1, "You cat", "Meeeeeeeeeeeeeeeow!", false),
    Room(2, "虫籠のカガステル", "No comment needed. Just join!", false),
    Room(3, "Mibik game server", "Mi mi mi! It's me.", false),
    Room(4, "My new world", "Don't join. It's private", true),
    Room(5, "Here is the best place.", "We need you.", false),
    Room(6, "Untitled room", "Join to title this room! ;)", false),
    Room(7, "Stormvik games", "Witaj! Jestem wikingiem.", true),
    Room(8, "Biohazard", "Be careful.", false),
    Room(9, "Fallout", null, true),
)

//event dotyczący wyniku przetwarzania jakiejś operacji asynchronicznej
sealed class ProcessEvent<out T> {
    data class Success<T>(val data: T): ProcessEvent<T>()
    data class Error(val err: String): ProcessEvent<Nothing>()
}

//zawiera zmienne przechowujące stan aplikacji
class NearNetViewModel: ViewModel() {

    //Rooms
    private val roomsMutable = MutableStateFlow(listOf<Room>())
    val rooms = roomsMutable.asStateFlow()

    //Filtered rooms
    private val searchRoomTextMutable = MutableStateFlow("")
    val searchRoomText = searchRoomTextMutable.asStateFlow()
    val filteredMyRoomsList : StateFlow<List<Room>> = combine(rooms, searchRoomText) { rooms, searchText ->
        if (searchText.isEmpty()) rooms
        else rooms.filter { it.name.contains(searchText, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    //Filtered discover rooms
    private val searchDiscoverTextMutable = MutableStateFlow("")
    val searchDiscoverText = searchDiscoverTextMutable.asStateFlow()
    var filteredDiscoverList : StateFlow<List<Room>> = combine(rooms, searchDiscoverText) { rooms, searchText ->
        if (searchText.isEmpty()) rooms
        else rooms.filter { it.name.contains(searchText, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    //Selected room
    private val selectedRoomMutable = MutableStateFlow<Room?>(null)
    val selectedRoom = selectedRoomMutable.asStateFlow()

    //Messages
    private val messagesMutable = MutableStateFlow(listOf<Message>())
    val messages = messagesMutable.asStateFlow()

    //Selected user
    private val selectedUserMutable = MutableStateFlow<User?>(null)
    val selectedUser = selectedUserMutable.asStateFlow()
    private val selectedUserEventMutable = MutableSharedFlow<ProcessEvent<User>>()
    val selectedUserEvent = selectedUserEventMutable.asSharedFlow()


    //constructor to VievModel
    init {

    }

    fun logInUser(login: String, password: String){
        viewModelScope.launch {
            // TODO Call asynchronous function to log user.
            //val user = logInUser(login, password)
            //selectedUserMutable.value = user
            val user = User(id = -1, login = "orci99", password = "abcd1234", name = "Orci Kätter")
            selectedUserMutable.value = user

            if (selectedUserMutable.value != null) {
                selectedUserEventMutable.emit(ProcessEvent.Success(user))
            }
            else {
                selectedUserEventMutable.emit(ProcessEvent.Error("Login failed. Incorrect login or password."))
            }
        }
    }
    fun loadMyRooms() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch my rooms here.
            //roomsMutable.value = getUserRoomList(idUser)
            roomsMutable.value = myRoomsList
        }
    }
    fun loadDiscoverRooms() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch discover rooms here.
            //roomsMutable.value = getRoomList()
            roomsMutable.value = discoverRoomsList
        }
    }
    fun createRoom(roomName : String, roomDescription : String){
        viewModelScope.launch {
            val createdRoom = Room(id = -1, name = roomName, description = roomDescription, isPrivate = false)
            // TODO Call asynchronous function to create room.
            // createdRoom.id = createRoom(createdRoom)
            myRoomsList += createdRoom
            discoverRoomsList += createdRoom
            selectRoom(createdRoom)
        }
    }
    fun filterMyRooms(filterText: String){
        searchRoomTextMutable.value = filterText
    }
    fun filterDiscoverRooms(filterText: String){
        searchDiscoverTextMutable.value = filterText
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
