package com.nearnet.ui.model

import android.content.Context
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nearnet.Message
import com.nearnet.Recent
import com.nearnet.Room
import com.nearnet.User
import com.nearnet.sessionlayer.logic.MessageUtils
import com.nearnet.sessionlayer.logic.RoomRepository
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



//var myRoomsList = listOf(
//    Room(0, "Stormvik games", "Witaj! Jestem wikingiem.", true),
//    Room(1, "You cat", "Meeeeeeeeeeeeeeeow!", false),
//    Room(2, "虫籠のカガステル", "No comment needed. Just join!", false),
//    Room(3, "Biohazard", "Be careful.", false),
//    Room(4, "Mibik game server", "Mi mi mi! It's me.", false),
//    Room(5, "Fallout", null, true),
//    Room(6, "My new world", "Don't join. It's private", true),
//    Room(7, "The Lord of the Rings: The Battle for the Middle Earth", "Elen", false),
//)
//var discoverRoomsList = listOf(
//    Room(0, "Adventure cat games", "Dołącz do kociej przygody.", true),
//    Room(1, "You cat", "Meeeeeeeeeeeeeeeow!", false),
//    Room(2, "虫籠のカガステル", "No comment needed. Just join!", false),
//    Room(3, "Mibik game server", "Mi mi mi! It's me.", false),
//    Room(4, "My new world", "Don't join. It's private", true),
//    Room(5, "Here is the best place.", "We need you.", false),
//    Room(6, "Untitled room", "Join to title this room! ;)", false),
//    Room(7, "Stormvik games", "Witaj! Jestem wikingiem.", true),
//    Room(8, "Biohazard", "Be careful.", false),
//    Room(9, "Fallout", null, true),
//)

//////////////////////////////POCZ
private val myRoomsListMutable = MutableStateFlow(listOf<Room>())
val myRoomsList = myRoomsListMutable.asStateFlow()

private val discoverRoomsListMutable = MutableStateFlow(listOf<Room>())
val discoverRoomsList = discoverRoomsListMutable.asStateFlow()
/////////////////////////////KON


//var messagesList = listOf(
//    Message("0", 0, "Orci Kätter", "Lorem ipsum dolor sit amet, consectetur adipiscing elit.", timestamp = "2025-09-28 15:42:17.123"),
//    Message("1", 0, "Mauris ", "Proin a eros quam. Ut sit amet ultrices nisi. Pellentesque ac tristique nisl, id imperdiet est. Integer scelerisque leo at blandit blandit.", timestamp = "2025-09-28 10:15:32.849"),
//    Message("2", 0, "Orci Kätter", "Fusce sed ligula turpis. Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Curabitur ac consequat nisi. Phasellus libero nibh, finibus non egestas in, egestas in lorem. Pellentesque nec facilisis erat, in pulvinar ipsum. Morbi congue viverra lectus quis fermentum. Duis sagittis est dapibus venenatis vestibulum.", timestamp = "2025-09-28 14:42:01.102"),
//    Message("0", 1, "Orci Kätter", "Curabitur ac consequat nisi. Phasellus libero nibh, finibus non egestas in, egestas in lorem.", timestamp = "2025-09-28 18:03:44.565"),
//    Message("0", 7, "Orci Kätter", "Duis sagittis est dapibus venenatis vestibulum. Non egestas in.", timestamp = "2025-09-28 18:03:44.565"),
//)

//event dotyczący wyniku przetwarzania jakiejś operacji asynchronicznej
sealed class ProcessEvent<out T> {
    data class Success<T>(val data: T): ProcessEvent<T>()
    data class Error(val err: String): ProcessEvent<Nothing>()
}

//zawiera zmienne przechowujące stan aplikacji
class NearNetViewModel(): ViewModel() {
    lateinit var repository: UserRepository
    lateinit var roomRepository: RoomRepository
    lateinit var messageUtils: MessageUtils

    //pozwala uzywac messageUtils bez uzywania LocalContext.current w ViewModelu
    fun initMessageUtils(context: Context) {
        if (!::messageUtils.isInitialized) {
            messageUtils = MessageUtils { UserRepository.getTokenFromPreferences(context) }
        }
    }

    //Selected user
    private val selectedUserMutable = MutableStateFlow<User?>(null)
    val selectedUser = selectedUserMutable.asStateFlow()
    private val selectedUserEventMutable = MutableSharedFlow<ProcessEvent<User?>>()
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
            // val user = User(id = -1, login = "orci99", password = "abcd1234", name = "Orci Kätter")
            try {
                val repoUser = repository.loginUser(login, password)
                if (repoUser != null) {
                    val uiUser = User(
                        id = repoUser.id,
                        login = repoUser.login,
                        password = repoUser.password,
                        name = repoUser.name
                    )
                    selectedUserMutable.value = uiUser
                    selectedUserEventMutable.emit(ProcessEvent.Success(uiUser))
                } else {
                    selectedUserEventMutable.emit(ProcessEvent.Error("Login failed"))
                }
            } catch (e: Exception) {
                Log.e("LoginError", "Failed to log in", e)
                selectedUserEventMutable.emit(ProcessEvent.Error("Login failed: ${e.message}"))
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
    fun logOutUser(){ //wylogowuje nawet jak coś poszło nie tak z internetem/serwerem
        viewModelScope.launch{
            val user = selectedUser.value
            var status : Boolean = false
            if (user != null) {
                //status = repository.logOutUser(user.id) //M
                status = true //
            }
            selectedUserMutable.value = null
            if (status == true){
                selectedUserEventMutable.emit(ProcessEvent.Success(null))
            }
            else {
                selectedUserEventMutable.emit(ProcessEvent.Error("Something went wrong while logging out."))
            }
        }
    }
    fun updateUser(){
        viewModelScope.launch {

        }
    }
    fun deleteUser(){
        viewModelScope.launch {
            val user = selectedUser.value
            var status : Boolean = false
            if (user != null) {
                //status = repository.deleteUser(user.id) //M
                status = true //
            }
            if (status == true) {
                selectedUserMutable.value = null
                //event Success
            } else {
                //event err
            }
        }
    }

    fun loadMyRooms() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch my rooms here.
            //roomsMutable.value = getUserRoomList(idUser)
            if (::roomRepository.isInitialized) {
                val roomsFromApi = roomRepository.getMyRooms()
                // konwersja RoomData -> Room
                val mappedRooms = roomsFromApi.map { rd ->
                    Room(
                        id = rd.idRoom,
                        name = rd.name,
                        description = null, //jak nizej
                        isPrivate = rd.isPrivate
                    )
                }
                myRoomsMutable.value = mappedRooms
            } else {
                Log.e("loadMyRooms", "RoomRepository is not initialized!")
            }
            //roomsMutable.value = myRoomsList
        }
    }
    fun loadDiscoverRooms() {
        viewModelScope.launch {
            // TODO Call asynchronous function to fetch discover rooms here.
            //roomsMutable.value = getRoomList()
            //roomsMutable.value = discoverRoomsList
            if (::roomRepository.isInitialized) {
                val roomsFromApi = roomRepository.getAllRooms()
                val mappedRooms = roomsFromApi.map { rd ->
                    Room(
                        id = rd.idRoom,
                        name = rd.name,
                        description = rd.avatar,    // avatar jako opis, trzeba zrobic takie same klasy serw/ui
                        isPrivate = rd.isPrivate
                    )
                }
                discoverRoomsMutable.value = mappedRooms
            } else {
                Log.e("loadDiscoverRooms", "RoomRepository is not initialized!")
            }
        }
    }
    fun createRoom(roomName : String, roomDescription : String){
        viewModelScope.launch {
//            val createdRoom = Room(id = -1, name = roomName, description = roomDescription, isPrivate = false)
//            // TODO Call asynchronous function to create room.
//            // createdRoom.id = createRoom(createdRoom)
//            myRoomsList += createdRoom
//            discoverRoomsList += createdRoom
//            selectRoom(createdRoom)
            if (::roomRepository.isInitialized) {
                val createdRoomData = roomRepository.addRoom(roomName, roomDescription)

                if (createdRoomData != null) {
                    val createdRoom = Room(
                        id = createdRoomData.idRoom,
                        name = createdRoomData.name,
                        description = createdRoomData.avatar, //tutaj dalem to co mamy jako avatar bo nie mamy opisu na serwie a mamy avatar
                        isPrivate = createdRoomData.isPrivate
                    )

                    myRoomsListMutable.value = myRoomsListMutable.value + createdRoom
                    discoverRoomsListMutable.value = discoverRoomsListMutable.value + createdRoom



                    //wybranie nowego pokoju od razu przelacza do wiadomosci, po utworzeniu
                    selectRoom(createdRoom)
                } else {
                    Log.e("createRoom", "❌ Nie udało się utworzyć pokoju na serwerze")
                }
            } else {
                Log.e("createRoom", "❌ RoomRepository nie jest zainicjalizowane!")
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
//            messagesMutable.value = listOf(
//                Message(0, "Orci Kätter", "Lorem ipsum dolor sit amet, consectetur adipiscing elit."),
//                Message(1, "Mauris ", "Proin a eros quam. Ut sit amet ultrices nisi. Pellentesque ac tristique nisl, id imperdiet est. Integer scelerisque leo at blandit blandit."),
//                Message(2, "Orci Kätter", "Fusce sed ligula turpis. Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Curabitur ac consequat nisi. Phasellus libero nibh, finibus non egestas in, egestas in lorem. Pellentesque nec facilisis erat, in pulvinar ipsum. Morbi congue viverra lectus quis fermentum. Duis sagittis est dapibus venenatis vestibulum.")
//            )

            if (!::messageUtils.isInitialized) {
                Log.e("loadMessages", "MessageUtils nie jest zainicjalizowany")
                return@launch
            } else {
                Log.d("loadMessages", "MessageUtils jest zainicjalizowany")
            }
            //pobieranie wiadomosci
            val response = try {
                Log.d("loadMessages", "Pobieram wiadomości dla pokoju=${room.id}")
                messageUtils.requestLastMessages(room.id)
            } catch (e: Exception) {
                Log.e("loadMessages", " Błąd podczas pobierania wiadomości dla pokoju=${room.id}", e)
                null
            }

            //sprawdzenie odpowiedzi
            if (response == null) {
                Log.e("loadMessages", "Serwer zwrócił pustą odpowiedź dla pokoju=${room.id}")
                return@launch
            } else {
                Log.d("loadMessages", "Otrzymano odpowiedź z serwera dla pokoju=${room.id}: $response")
            }

            // sprawdzenie listy wiadomosci
            val messageList = response.`package`?.messageList
            if (messageList.isNullOrEmpty()) {
                Log.w("loadMessages", "Brak wiadomości w historii dla pokoju=${room.id}")
            } else {
                Log.d("loadMessages", "Serwer zwrócił ${messageList.size} wiadomości dla pokoju: ${room.id}")
            }

            // mapowanie do UI
            val messagesFromApi = messageList?.map { payload ->
                Message(
                    id = payload.timestamp,
                    userNameSender = payload.userId,
                    content = payload.data
                )
            } ?: emptyList()

            // aktualizacja stanu
            messagesMutable.value = messagesFromApi
        }
    }
    fun sendMessage(messageText : String, room : Room){
        viewModelScope.launch{
            //val message = Message (id = -1, userNameSender = "Orci Kätter", content = messageText)
            // TODO Call asynchronous function to send messages
            //sendMessage(room.id, message)
            //messagesMutable.value += message
            if (!::messageUtils.isInitialized) {
                Log.e("sendMessage", "MessageUtils nie jest zainicjalizowane!")
                return@launch
            }

            val userName = selectedUser.value?.name ?: "brak usera"
            val timestamp = System.currentTimeMillis()

            // UI message
            val uiMessage = com.nearnet.Message(
                id = timestamp.toString(),
                userNameSender = userName,
                content = messageText
            )

            // Backend message
            val backendMessage = com.nearnet.sessionlayer.data.model.Message(
                username = userName,
                message = messageText,
                timestamp = timestamp,
                roomId = room.id
            )

            Log.d("sendMessage", "Wysyłam wiadomość na backend: $backendMessage")

            try {
                val success = messageUtils.sendMessage(room.id, backendMessage)

                if (success) {
                    Log.d("sendMessage", "Wiadomość wysłana poprawnie")
                    messagesMutable.value += uiMessage
                } else {
                    Log.e("sendMessage", "Nie udało się wysłać wiadomości")
                }
            } catch (e: Exception) {
                Log.e("sendMessage", "Exception w sendMessage", e)
            }
        }
    }
//    fun loadRecentMessages() {
//        viewModelScope.launch {
//            // TODO Call asynchronous function to fetch recent messages here.
//            //recentMutable.value = getRecentMessages(idUser) //zwraca listę trójek (Room, lastMessage,username)
//            //funkcja: grupuje wiadomości po pokojach, dla każdej grupy uzyskuje dane pokoju, a następnie tworzy trójki
//            //typu (wiadomość, pokój, nazwa użytkownika), w SQL join pokoju do wiadomości i do usera, i groupby po pokojach ,
//            //a potem select na te trójki
//            recentMutable.value = messagesList //
//                .groupBy { message -> message.idRoom }
//                .mapValues { roomMessages ->
//                    val message = roomMessages.value.maxBy { message -> message.timestamp }
//                    val room = myRoomsMutable.find { room -> room.id == message.idRoom }
//                    Recent(message = message, room = room, username = message.userNameSender)
//                }.values
//                .toList()
//                .sortedByDescending { recent -> recent.message.timestamp }
//        }
//    }

}

val LocalViewModel = staticCompositionLocalOf<NearNetViewModel> {
    error("No NearNetViewModel provided")
}
