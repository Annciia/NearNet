package com.nearnet.ui.model

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearnet.Message
import com.nearnet.Recent
import com.nearnet.Room
import com.nearnet.User
import com.nearnet.sessionlayer.logic.UserRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

var messagesList = listOf(
    Message(0, 0, "Orci Kätter", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.", timestamp = "2025-09-28 15:42:17.123"),
    Message(1, 0, "Mauris ", "Proin a eros quam. Ut sit amet ultrices nisi. Pellentesque ac tristique nisl, id imperdiet est. Integer scelerisque leo at blandit blandit.", timestamp = "2025-09-28 10:15:32.849"),
    Message(2, 0, "Orci Kätter", "Fusce sed ligula turpis. Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Curabitur ac consequat nisi. Phasellus libero nibh, finibus non egestas in, egestas in lorem. Pellentesque nec facilisis erat, in pulvinar ipsum. Morbi congue viverra lectus quis fermentum. Duis sagittis est dapibus venenatis vestibulum.", timestamp = "2025-09-28 14:42:01.102"),
    Message(0, 1, "Orci Kätter", "Curabitur ac consequat nisi. Phasellus libero nibh, finibus non egestas in, egestas in lorem.", timestamp = "2025-09-28 18:03:44.565"),
    Message(0, 7, "Orci Kätter", "Duis sagittis est dapibus venenatis vestibulum. Non egestas in.", timestamp = "2025-09-28 18:03:44.565"),
)

//event dotyczący wyniku przetwarzania jakiejś operacji asynchronicznej
sealed class ProcessEvent<out T> {
    data class Success<T>(val data: T): ProcessEvent<T>()
    data class Error(val err: String): ProcessEvent<Nothing>()
}

//zawiera zmienne przechowujące stan aplikacji
class NearNetViewModel(): ViewModel() {
    lateinit var repository: UserRepository

    //Selected user
    private val selectedUserMutable = MutableStateFlow<User?>(null)
    val selectedUser = selectedUserMutable.asStateFlow()
    private val selectedUserEventMutable = MutableSharedFlow<ProcessEvent<User>>()
    val selectedUserEvent = selectedUserEventMutable.asSharedFlow()

    //Register user
    private val registerUserEventMutable = MutableSharedFlow<ProcessEvent<Unit>>()
    val registerUserEvent = registerUserEventMutable.asSharedFlow()

    //Rooms
    private val myRoomsMutable = MutableStateFlow(listOf<Room>())
    val myRooms = myRoomsMutable.asStateFlow()

    //Discover rooms
    private val discoverRoomsMutable = MutableStateFlow(listOf<Room>())
    val discoverRooms = discoverRoomsMutable.asStateFlow()

    //Filtered my rooms
    private val searchMyRoomsTextMutable = MutableStateFlow("")
    val searchMyRoomsText = searchMyRoomsTextMutable.asStateFlow()
    val filteredMyRoomsList : StateFlow<List<Room>> = combine(myRooms, searchMyRoomsText) { rooms, searchText ->
        if (searchText.isEmpty()) rooms
        else rooms.filter { it.name.contains(searchText, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    //Filtered discover rooms
    private val searchDiscoverTextMutable = MutableStateFlow("")
    val searchDiscoverText = searchDiscoverTextMutable.asStateFlow()
    var filteredDiscoverList : StateFlow<List<Room>> = combine(discoverRooms, searchDiscoverText) { rooms, searchText ->
        if (searchText.isEmpty()) rooms
        else rooms.filter { it.name.contains(searchText, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    //Selected room
    private val selectedRoomMutable = MutableStateFlow<Room?>(null)
    val selectedRoom = selectedRoomMutable.asStateFlow()
    private val selectedRoomEventMutable = MutableSharedFlow<ProcessEvent<Room>>()
    val selectedRoomEvent = selectedRoomEventMutable.asSharedFlow()

    //Register room
    private val registerRoomEventMutable = MutableSharedFlow<ProcessEvent<Room>>()
    val registerRoomEvent = registerRoomEventMutable.asSharedFlow()

    //Messages
    private val messagesMutable = MutableStateFlow(listOf<Message>())
    val messages = messagesMutable.asStateFlow()

    //Recent
    private val recentMutable = MutableStateFlow(listOf<Recent>())
    val recent = recentMutable.asStateFlow()

    //constructor to VievModel
    init {

    }

    fun logInUser(login: String, password: String){
        viewModelScope.launch {
            // TODO Call asynchronous function to log user.
            //val user = logInUser(login, password)
            //selectedUserMutable.value = user
            val user = User(id = -1, login = "orci99", password = "abcd1234", name = "Orci Kätter") //
            selectedUserMutable.value = user //

            if (selectedUserMutable.value != null) {
                selectedUserEventMutable.emit(ProcessEvent.Success(user))
            }
            else {
                selectedUserEventMutable.emit(ProcessEvent.Error("Login failed. Incorrect login or password."))
            }
        }
    }
    fun registerUser(login: String, password: String){
        viewModelScope.launch {
            // TODO Call asynchronous function to register user. //DONE
            val status : Boolean = repository.registerUser(login, password)
            //val status : Boolean = true //
            if (status == true){
                registerUserEventMutable.emit(ProcessEvent.Success(Unit))
            }
            else {
                registerUserEventMutable.emit(ProcessEvent.Error("Failed to create account. Please try again."))
            }
        }
    }
    fun loadMyRooms() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch my rooms here.
            //roomsMutable.value = getUserRoomList(idUser)
            myRoomsMutable.value = myRoomsList //
        }
    }
    fun loadDiscoverRooms() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch discover rooms here.
            //discoverRoomsMutable.value = getRoomList()
            discoverRoomsMutable.value = discoverRoomsList //
        }
    }
    fun createRoom(roomName : String, roomDescription : String){
        viewModelScope.launch {
            var roomId : Int? = null
            val createdRoom = Room(id = -1, name = roomName, description = roomDescription, isPrivate = false)
            // TODO Call asynchronous function to create room.
            // roomId = createRoom(createdRoom)
            roomId = -1 //

            if (roomId != null) {
                createdRoom.id = roomId
                myRoomsList += createdRoom //
                discoverRoomsList += createdRoom //
                registerRoomEventMutable.emit(ProcessEvent.Success(createdRoom))
            } else {
                registerRoomEventMutable.emit(ProcessEvent.Error("Something went wrong while creating the room."))
            }
        }
    }
    fun filterMyRooms(filterText: String){
        searchMyRoomsTextMutable.value = filterText
    }
    fun filterDiscoverRooms(filterText: String){
        searchDiscoverTextMutable.value = filterText
    }
    fun selectRoom(room : Room) {
        viewModelScope.launch {
            loadMessages(room)
            selectedRoomMutable.value = room

            if (selectedRoomMutable.value != null) {
                selectedRoomEventMutable.emit(ProcessEvent.Success(room))
            } else {
                selectedRoomEventMutable.emit(ProcessEvent.Error("Failed to enter the room."))
            }
        }
    }
    fun loadMessages(room: Room){
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch messages here. Przefiltrowana i posortowana lista potrzebna.
            //messagesMutable.value = getMessageHistory(idRoom = room.id, offset = 0, numberOfMessages = -1)
            messagesMutable.value = messagesList.filter { message -> message.idRoom == room.id }.sortedBy { message -> message.timestamp } //
        }
    }
    fun sendMessage(messageText : String, room : Room){
        viewModelScope.launch{
            val message = Message (id = -1, room.id, userNameSender = "Orci Kätter", content = messageText, timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")))
            messagesMutable.value += message
            // TODO Call asynchronous function to send messages
            //sendMessage(room.id, message)
            messagesList += message //
        }
    }
    fun loadRecentMessages() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch recent messages here.
            //recentMutable.value = getRecentMessages(idUser) //zwraca listę trójek (Room, lastMessage,username)
            //funkcja: grupuje wiadomości po pokojach, dla każdej grupy uzyskuje dane pokoju, a następnie tworzy trójki
            //typu (wiadomość, pokój, nazwa użytkownika), w SQL join pokoju do wiadomości i do usera, i groupby po pokojach ,
            //a potem select na te trójki
            recentMutable.value = messagesList //
                .groupBy { message -> message.idRoom }
                .mapValues { roomMessages ->
                    val message = roomMessages.value.maxBy { message -> message.timestamp }
                    val room = myRoomsList.find { room -> room.id == message.idRoom }
                    Recent(message = message, room = room, username = message.userNameSender)
                 }.values
                .toList()
                .sortedByDescending { recent -> recent.message.timestamp }
        }
    }

}

val LocalViewModel = staticCompositionLocalOf<NearNetViewModel> {
    error("No NearNetViewModel provided")
}
